package com.neoutils.finsight.database.repository

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.entity.AccountEntity
import com.neoutils.finsight.database.entity.CurrencyEntity
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.domain.error.CurrencyError
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.feature.backup.api.DestructiveAction
import com.neoutils.finsight.feature.backup.api.PreventiveBackup
import com.neoutils.finsight.feature.backup.api.PreventiveCaptureException
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The two destructive actions this feature owns — deleting a currency, removing an
 * observation — and the copy that has to exist **before** either of them happens.
 *
 * The witness below answers by reading the live archive at the moment it is asked, which
 * is what makes this a test about *order* and not about wiring. A hook installed after
 * the deletion would still be called, and would still name the right action; it would
 * just find nothing left to copy, which is the whole defect (design D6).
 */
class PreventiveCaptureTest {

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @AfterTest
    fun tearDown() = db.close()

    private class FixedBase(code: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(code)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) { state.value = code }
    }

    private class FakeSyncState : IRateSyncStateRepository {
        private val flow = MutableStateFlow(RateSyncState())
        override fun observe(): StateFlow<RateSyncState> = flow
        override suspend fun record(state: RateSyncState) { flow.value = state }
    }

    /** What the archive still held at the instant the copy was asked for. */
    private data class Sighting(
        val action: DestructiveAction,
        val currencies: List<String>,
        val ratesNamingDollar: Int,
    )

    /**
     * A vault that, instead of writing a file, looks at what it was asked to protect.
     *
     * [refuses] is the copy that was owed and could not be taken: it refuses the only way
     * the contract offers, by throwing, and whoever called it must not go on.
     */
    private inner class Witness(private val refuses: Boolean = false) : PreventiveBackup {

        val seen = mutableListOf<Sighting>()

        override suspend fun captureBefore(action: DestructiveAction) {
            seen += Sighting(
                action = action,
                currencies = db.currencyDao().getAll().map { it.code },
                ratesNamingDollar = db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"),
            )

            if (refuses) {
                throw PreventiveCaptureException(
                    reason = UiText.Raw("nowhere to write"),
                    message = "nowhere to write",
                )
            }
        }
    }

    private fun rates(backup: PreventiveBackup) = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = FixedBase("BRL"),
        preventiveBackup = backup,
    )

    // The same witness reaches the rate archive the use case reads through, so a second
    // capture from anywhere inside the deletion would show up as a second sighting — a
    // copy per row touched instead of one per user action.
    private fun deleteCurrency(backup: PreventiveBackup) = DeleteCurrencyUseCase(
        repository = CurrencyRepository(
            database = db,
            dao = db.currencyDao(),
            exchangeRateDao = db.exchangeRateDao(),
        ),
        exchangeRateRepository = rates(backup),
        rateSyncStateRepository = FakeSyncState(),
        accountDao = db.accountDao(),
        budgetDao = db.budgetDao(),
        preventiveBackup = backup,
    )

    private val march = LocalDate(2026, 3, 14)

    private suspend fun seedCurrencies(vararg codes: String) {
        codes.forEach { db.currencyDao().upsert(CurrencyEntity(code = it, symbol = it)) }
    }

    private suspend fun seedRate(
        from: String,
        to: String,
        value: Double,
        date: LocalDate = march,
    ): ExchangeRate {
        val archive = rates(PreventiveBackup.None)

        archive.save(
            ExchangeRate(
                currency = from,
                counterCurrency = to,
                date = date,
                rate = value,
                source = ExchangeRate.Source.USER,
            )
        )

        return archive.observeAll().first().first { it.currency == from && it.date == date }
    }

    @Test
    fun `deleting a currency copies the archive while the currency and its rates are still in it`() =
        runTest {
            seedCurrencies("BRL", "USD")
            seedRate("USD", "BRL", 5.5)
            seedRate("EUR", "USD", 1.1)

            val witness = Witness()

            assertEquals(null, deleteCurrency(witness)("USD").leftOrNull())

            assertEquals(1, witness.seen.size, "one copy per user action, not per row")

            val sighting = witness.seen.single()

            assertEquals(DestructiveAction.DELETE_CURRENCY, sighting.action)
            assertTrue("USD" in sighting.currencies, "the currency was already gone when asked")
            assertEquals(2, sighting.ratesNamingDollar, "the observations were already gone when asked")

            assertEquals(null, db.currencyDao().getByCode("USD"))
            assertEquals(0, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
        }

    @Test
    fun `a refused copy leaves the currency and every observation naming it`() = runTest {
        seedCurrencies("BRL", "USD")
        seedRate("USD", "BRL", 5.5)
        seedRate("EUR", "USD", 1.1)

        val witness = Witness(refuses = true)

        assertFailsWith<PreventiveCaptureException> { deleteCurrency(witness)("USD") }

        assertTrue(db.currencyDao().exists("USD"))
        assertEquals(2, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
    }

    @Test
    fun `a deletion the domain refuses asks for no copy`() = runTest {
        seedCurrencies("BRL", "USD")
        db.accountDao().insert(
            AccountEntity(
                name = "Conta USD",
                type = AccountEntity.Type.ASSET,
                currency = "USD",
                iconKey = "wallet",
            )
        )

        val witness = Witness()

        assertEquals(CurrencyError.DENOMINATED_BY_ACCOUNT, deleteCurrency(witness)("USD").leftOrNull())

        assertTrue(witness.seen.isEmpty(), "nothing was destroyed, so nothing was owed")
    }

    @Test
    fun `removing an observation copies the archive while the row is still in it`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)

        val witness = Witness()

        rates(witness).remove(rate)

        val sighting = witness.seen.single()

        assertEquals(DestructiveAction.REMOVE_EXCHANGE_RATE, sighting.action)
        assertEquals(1, sighting.ratesNamingDollar, "the observation was already gone when asked")

        assertEquals(0, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
    }

    @Test
    fun `a refused copy leaves the observation in the archive`() = runTest {
        seedCurrencies("BRL", "USD")
        val rate = seedRate("USD", "BRL", 5.5)

        assertFailsWith<PreventiveCaptureException> { rates(Witness(refuses = true)).remove(rate) }

        assertEquals(1, db.exchangeRateDao().countByCurrencyOnEitherEnd("USD"))
    }
}
