package com.neoutils.finsight.database

import androidx.room.Room
import androidx.room.execSQL
import androidx.room.immediateTransaction
import androidx.room.useWriterConnection
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.entity.InstallmentEntity
import com.neoutils.finsight.database.entity.RecurringEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNull

/**
 * Characterization of what removing an installment or a recurring leaves behind.
 *
 * The `ON DELETE SET NULL` foreign keys do half of this today, and they do not
 * survive the move of `transactions` to the ledger module, where the parent entities
 * are not visible (design D12). So the property is asserted **with and without the
 * key**: with foreign keys enforced, as production runs today, and with enforcement
 * off, which is the schema the rebuild will leave behind. Both passing is what makes
 * dropping the keys a step with no observable change.
 *
 * The other half was never free: the keys only ever cleared the id, leaving
 * `installmentNumber` and `recurringCycle` pointing at a facade that was gone.
 */
class FacadeDetachmentTest {

    private val file: File = File.createTempFile("finsight-detachment", ".db").also { it.delete() }

    @AfterTest
    fun tearDown() {
        file.delete()
    }

    @Test
    fun `given foreign keys enforced when an installment is removed then no reference dangles`() =
        assertNothingDangles(foreignKeys = true, facade = Facade.Installment)

    @Test
    fun `given no foreign key when an installment is removed then no reference dangles`() =
        assertNothingDangles(foreignKeys = false, facade = Facade.Installment)

    @Test
    fun `given foreign keys enforced when a recurring is removed then no reference dangles`() =
        assertNothingDangles(foreignKeys = true, facade = Facade.Recurring)

    @Test
    fun `given no foreign key when a recurring is removed then no reference dangles`() =
        assertNothingDangles(foreignKeys = false, facade = Facade.Recurring)

    /**
     * `TransactionRepository.removeRow` calls the installment removal from inside a
     * writer transaction it already holds. Now that the removal opens one of its own,
     * the nesting has to resolve to a savepoint rather than to a second connection —
     * a deadlock here would only show up when deleting the last transaction of an
     * installment, which is not a path a compiler can check.
     */
    @Test
    fun `given a writer transaction already held when the removal opens its own then it nests`() = runTest {
        val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        val installmentId = database.installmentDao()
            .insert(InstallmentEntity(count = 1, totalAmount = 100.0))
        val transactionId = database.transactionDao().insert(
            TransactionEntity(
                title = "Parcela",
                date = LocalDate(2024, 1, 10),
                installmentId = installmentId,
                installmentNumber = 1,
            )
        )

        database.useWriterConnection { outer ->
            outer.immediateTransaction {
                database.transactionDao().deleteById(transactionId)
                database.useWriterConnection { inner ->
                    inner.immediateTransaction {
                        database.transactionDao().detachFromInstallment(installmentId)
                        database.installmentDao().deleteById(installmentId)
                    }
                }
            }
        }

        assertNull(database.installmentDao().getById(installmentId))

        database.close()
        file.delete()
    }

    private enum class Facade { Installment, Recurring }

    private fun assertNothingDangles(foreignKeys: Boolean, facade: Facade) = runTest {
        val database = Room.databaseBuilder<AppDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .setQueryCoroutineContext(Dispatchers.IO)
            .build()

        database.useWriterConnection { connection ->
            connection.execSQL("PRAGMA foreign_keys = ${if (foreignKeys) "ON" else "OFF"}")
        }

        val installmentId = database.installmentDao()
            .insert(InstallmentEntity(count = 3, totalAmount = 300.0))
        val recurring = RecurringEntity(
            type = RecurringEntity.Type.EXPENSE,
            amount = 100.0,
            title = "Assinatura",
            dayOfMonth = 10,
            categoryId = null,
            accountId = null,
            creditCardId = null,
        )
        val recurringId = database.recurringDao().insert(recurring)
        val transactionId = database.transactionDao().insert(
            TransactionEntity(
                title = "Parcela",
                date = LocalDate(2024, 1, 10),
                installmentId = installmentId,
                installmentNumber = 1,
                recurringId = recurringId,
                recurringCycle = 1,
            )
        )

        // Exactly what the repository removal path does, in one unit of work.
        database.useWriterConnection { connection ->
            connection.immediateTransaction {
                when (facade) {
                    Facade.Installment -> {
                        database.transactionDao().detachFromInstallment(installmentId)
                        database.installmentDao().deleteById(installmentId)
                    }

                    Facade.Recurring -> {
                        database.transactionDao().detachFromRecurring(recurringId)
                        database.recurringDao().delete(recurring.copy(id = recurringId))
                    }
                }
            }
        }

        val transaction = database.transactionDao().getById(transactionId)!!
        when (facade) {
            Facade.Installment -> {
                assertNull(transaction.installmentId)
                assertNull(transaction.installmentNumber)
            }

            Facade.Recurring -> {
                assertNull(transaction.recurringId)
                assertNull(transaction.recurringCycle)
            }
        }

        database.close()
        file.delete()
    }
}
