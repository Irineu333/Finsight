package com.neoutils.finsight.domain.ledger

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * An installment describes its transactions, so removing one of them makes it lie
 * until this puts it right. What the ledger contributes is only the *when* — inside
 * the write transaction that removed the row — and this is the rule itself.
 */
class InstallmentRemovalReconcilerTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest fun tearDown() = db.close()

    private val reconciler = InstallmentRemovalReconciler(
        installmentRepository = InstallmentRepository(database = db, installmentDao = db.installmentDao()),
        installmentDao = db.installmentDao(),
    )

    private val card = Account(id = 1, name = "Card", type = AccountType.LIABILITY)

    /** Six shares of R$ 100 under one installment, as `AddInstallmentUseCase` writes them. */
    private suspend fun seed(count: Int = 6, totalAmount: Double = 600.0): Long {
        db.accountDao().insert(AccountEntity(id = 1, name = "Card", type = AccountEntity.Type.LIABILITY))
        val installmentId = db.installmentDao().insert(InstallmentEntity(count = count, totalAmount = totalAmount))
        repeat(count) { index ->
            val transactionId = db.transactionDao().insert(
                TransactionEntity(
                    title = "Sofa",
                    date = LocalDate(2026, 1, 10),
                    installmentId = installmentId,
                    installmentNumber = index + 1,
                )
            )
            db.entryDao().insert(
                EntryEntity(transactionId = transactionId, accountId = 1, amount = -10_000)
            )
        }
        return installmentId
    }

    private fun removed(installmentId: Long, shareCents: Long = -10_000) = Transaction(
        id = 99,
        title = "Sofa",
        date = LocalDate(2026, 1, 10),
        installmentId = installmentId,
        installmentNumber = 3,
        entries = listOf(Entry(id = 1, transactionId = 99, account = card, amount = shareCents)),
    )

    @Test
    fun `removing one share leaves the installment describing what is left`() = runTest {
        val installmentId = seed()
        // The row is already gone when the hook runs — that is the contract.
        db.transactionDao().deleteById(db.transactionDao().getAll().first().id)

        reconciler.onRemoved(removed(installmentId))

        val installment = db.installmentDao().getById(installmentId)!!
        assertEquals(5, installment.count)
        assertEquals(500.0, installment.totalAmount)
    }

    @Test
    fun `removing the last share removes the installment`() = runTest {
        val installmentId = seed(count = 1, totalAmount = 100.0)
        db.transactionDao().deleteById(db.transactionDao().getAll().first().id)

        reconciler.onRemoved(removed(installmentId))

        assertNull(db.installmentDao().getById(installmentId))
    }

    @Test
    fun `a transaction that belongs to no installment is none of its business`() = runTest {
        val installmentId = seed()

        reconciler.onRemoved(removed(installmentId).copy(installmentId = null))

        val installment = db.installmentDao().getById(installmentId)!!
        assertEquals(6, installment.count)
        assertEquals(600.0, installment.totalAmount)
    }

    @Test
    fun `the count comes from the rows, not from the stored copy`() = runTest {
        // The stored count is the thing being corrected, so trusting it would make
        // the correction inherit whatever drift it already had.
        val installmentId = seed()
        db.installmentDao().updateById(id = installmentId, count = 99, totalAmount = 600.0)
        db.transactionDao().deleteById(db.transactionDao().getAll().first().id)

        reconciler.onRemoved(removed(installmentId))

        assertEquals(5, db.installmentDao().getById(installmentId)!!.count)
    }
}
