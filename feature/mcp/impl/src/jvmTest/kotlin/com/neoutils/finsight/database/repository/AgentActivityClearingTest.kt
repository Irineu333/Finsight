package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import com.neoutils.finsight.database.mapper.AgentActivityMapper
import com.neoutils.finsight.domain.model.DimensionKind
import com.neoutils.finsight.feature.mcp.api.AgentActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Clock

/**
 * Clearing the log, and the one thing it must never do.
 *
 * The log is a trace of who did what; the ledger is what was done. Emptying the first is offered
 * to the user precisely because it cannot reach the second: discarding the record of an act does
 * not undo the act. This test is that sentence, executed — a ledger is built, the log is filled
 * with acts that describe it, the log is emptied, and every posting is read back and compared.
 */
class AgentActivityClearingTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private val repository = AgentActivityRepository(
        dao = db.agentActivityDao(),
        mapper = AgentActivityMapper(),
        clock = Clock.System,
    )

    /** One balanced transaction classified by a category dimension, plus a second account. */
    private suspend fun seedLedger(): Long {
        val wallet = db.accountDao().insert(AccountEntity(name = "Carteira", currency = "BRL"))
        val expenses = db.accountDao().insert(
            AccountEntity(name = "Despesas", type = AccountEntity.Type.EXPENSE, currency = "BRL")
        )
        val dimension = db.dimensionDao().emit(DimensionKind.CATEGORY)
        val transaction = db.transactionDao().insert(
            TransactionEntity(title = "Feira", date = LocalDate(2026, 1, 10))
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transaction, accountId = expenses, amount = 5_000, currency = "BRL", dimensionId = dimension),
                EntryEntity(transactionId = transaction, accountId = wallet, amount = -5_000, currency = "BRL"),
            )
        )
        return wallet
    }

    private suspend fun ledgerSnapshot() = Triple(
        db.accountDao().getAllAccountsIncludingClosed(),
        db.transactionDao().getAll(),
        db.entryDao().getAll(),
    )

    @Test
    fun `clearing empties the log`() = runTest {
        seedLedger()
        repository.record("create_transaction", "Feira de 50,00 na Carteira", AgentActivity.Outcome.APPLIED)
        repository.record("delete_account", "Apagar a Carteira", AgentActivity.Outcome.REFUSED, detail = "tem lançamentos")
        assertEquals(2, repository.observeAll().first().size)

        repository.clear()

        assertEquals(emptyList(), repository.observeAll().first())
    }

    @Test
    fun `clearing alters no posting`() = runTest {
        val wallet = seedLedger()
        repository.record(
            operation = "create_transaction",
            summary = "Feira de 50,00 na Carteira",
            outcome = AgentActivity.Outcome.APPLIED,
            reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, 1),
        )
        val before = ledgerSnapshot()
        val balanceBefore = db.entryDao().balanceOf(wallet)
        assertTrue(before.third.isNotEmpty(), "the fixture has to have postings to lose")

        repository.clear()

        assertEquals(before, ledgerSnapshot(), "every account, transaction and entry is untouched")
        assertEquals(balanceBefore, db.entryDao().balanceOf(wallet), "and the figure they produce")
        assertEquals(-5_000L, db.entryDao().balanceOf(wallet))
    }

    /**
     * The other half of "it is not accounting truth": what the log referenced may be deleted, and
     * the ledger never asks the log for permission. The act stays on record, naming something
     * that has stopped existing — which is itself worth showing.
     */
    @Test
    fun `the log never keeps a posting from being deleted`() = runTest {
        seedLedger()
        val transaction = db.transactionDao().getAll().single()
        repository.record(
            operation = "create_transaction",
            summary = "Feira de 50,00 na Carteira",
            outcome = AgentActivity.Outcome.APPLIED,
            reference = AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, transaction.id),
        )

        db.transactionDao().deleteById(transaction.id)

        assertEquals(emptyList(), db.transactionDao().getAll())
        val act = repository.observeAll().first().single()
        assertEquals(
            AgentActivity.Reference(AgentActivity.Reference.Kind.TRANSACTION, transaction.id),
            act.reference,
            "the act is still on record, and still names what it produced",
        )
    }
}
