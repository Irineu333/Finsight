package com.neoutils.finsight.domain.usecase

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountCurrencyRelabelDao
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.EntryEntity
import com.neoutils.finsight.database.entity.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The step that makes a legacy database say what its user always read — and the four things
 * that decide whether it may do so at all.
 *
 * It is the one place in the app allowed to change an account's currency, and that permission
 * is narrow on purpose: a migration may do what the runtime forbids only because it happens
 * before the currency of those accounts was ever an observable fact.
 */
class RelabelLegacyAccountCurrencyUseCaseTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    @Test
    fun `a legacy database on a foreign region is relabelled, and the ledger stays balanced`() = runTest {
        seedLegacyLedger()

        relabelOn(locale = "USD")()

        assertEquals(listOf("USD", "USD"), currencies())
        // Relabelling, not conversion: every leg's denomination moved together, so the sum
        // per currency is still zero — which is the invariant the whole change refuses to
        // make an exception to.
        assertTrue(everyTransactionBalancesPerCurrency())
        assertEquals(listOf(-15680L, 15680L), amounts(), "not a cent moved")
    }

    @Test
    fun `the log holds exactly one row per account relabelled`() = runTest {
        seedLegacyLedger()

        relabelOn(locale = "USD")()

        val log = db.accountCurrencyRelabelDao().relabelLog()
        assertEquals(2, log.size)
        assertEquals(listOf("BRL", "BRL"), log.map { it.previousCurrency })
        assertEquals(listOf("USD", "USD"), log.map { it.newCurrency })
    }

    @Test
    fun `a device in the region of origin is not touched, and logs nothing`() = runTest {
        seedLegacyLedger()

        relabelOn(locale = "BRL")()

        assertEquals(listOf("BRL", "BRL"), currencies())
        assertTrue(db.accountCurrencyRelabelDao().relabelLog().isEmpty())
    }

    @Test
    fun `a currency the app does not offer does not fire the step`() = runTest {
        seedLegacyLedger()

        // Yen is a real currency the catalog deliberately excludes — zero decimal places
        // against an app that holds every amount at base 100.
        relabelOn(locale = "JPY")()

        assertEquals(listOf("BRL", "BRL"), currencies())
        assertTrue(db.accountCurrencyRelabelDao().relabelLog().isEmpty())
    }

    @Test
    fun `a second run does not repeat, even after the region changes again`() = runTest {
        seedLegacyLedger()
        relabelOn(locale = "USD")()

        // The trip continues. Relabelling again would be exactly the silent restatement of
        // meaning that the base currency is forbidden from doing.
        relabelOn(locale = "EUR")()

        assertEquals(listOf("USD", "USD"), currencies())
        assertEquals(2, db.accountCurrencyRelabelDao().relabelLog().size)
    }

    @Test
    fun `a crash after the update and before the claim leaves everything as it was`() = runTest {
        seedLegacyLedger()

        val exploding = RelabelLegacyAccountCurrencyUseCase(
            database = db,
            dao = ExplodingAfterRelabel(db.accountCurrencyRelabelDao()),
            deviceCurrency = { "USD" },
        )
        runCatching { exploding() }

        // The point of the claim living in the database: it rolls back with the work. A flag
        // in the settings store would have left the rows rewritten and the app believing the
        // step never ran.
        assertEquals(listOf("BRL", "BRL"), currencies())
        assertTrue(db.accountCurrencyRelabelDao().relabelLog().isEmpty())

        // And because nothing was claimed, the step is still available to run properly.
        relabelOn(locale = "USD")()
        assertEquals(listOf("USD", "USD"), currencies())
    }

    private fun relabelOn(locale: String) = RelabelLegacyAccountCurrencyUseCase(
        database = db,
        dao = db.accountCurrencyRelabelDao(),
        deviceCurrency = { locale },
    )

    /** Two accounts and one balanced transaction, all of it in the legacy currency. */
    private suspend fun seedLegacyLedger() {
        val wallet = db.accountDao().insert(
            AccountEntity(name = "Wallet", type = AccountEntity.Type.ASSET, currency = "BRL")
        )
        val food = db.accountDao().insert(
            AccountEntity(name = "Food", type = AccountEntity.Type.EXPENSE, currency = "BRL")
        )
        val transactionId = db.transactionDao().insert(
            TransactionEntity(title = "Supermarket", date = LocalDate(2026, 3, 20))
        )
        db.entryDao().insertAll(
            listOf(
                EntryEntity(transactionId = transactionId, accountId = wallet, amount = -15680, currency = "BRL"),
                EntryEntity(transactionId = transactionId, accountId = food, amount = 15680, currency = "BRL"),
            )
        )
    }

    private suspend fun currencies() = db.accountDao().getAllLedgerAccounts().map { it.currency }.sorted()

    private suspend fun amounts() = db.entryDao().getAll().map { it.amount }.sorted()

    /** The invariant itself, restated here rather than borrowed: Σ = 0 per transaction and currency. */
    private suspend fun everyTransactionBalancesPerCurrency() = db.entryDao().getAll()
        .groupBy { it.transactionId to it.currency }
        .values
        .all { entries -> entries.sumOf { it.amount } == 0L }

    /**
     * A DAO that dies exactly where the danger is: after the rows have been rewritten and
     * before the step has claimed to have run. Everything before it must come back.
     */
    private class ExplodingAfterRelabel(
        private val delegate: AccountCurrencyRelabelDao,
    ) : AccountCurrencyRelabelDao by delegate {
        override suspend fun relabel(from: String, to: String) {
            delegate.relabel(from, to)
            error("crash between the update and the claim")
        }
    }
}
