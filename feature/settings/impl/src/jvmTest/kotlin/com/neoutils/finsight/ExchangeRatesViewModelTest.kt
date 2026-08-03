@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight

import androidx.room.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import app.cash.turbine.test
import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.RatePair
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesUiState
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.minus
import kotlinx.datetime.toLocalDateTime
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The archive's entry view, over a real database — because what it asserts is *which*
 * observation answers for a pair, and that is decided by the archive's query. A fake would
 * answer whatever the test seeded it with, which is precisely the question being asked.
 */
class ExchangeRatesViewModelTest {

    private val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val db = Room.inMemoryDatabaseBuilder<AppDatabase>()
        .setDriver(BundledSQLiteDriver())
        .setQueryCoroutineContext(Dispatchers.IO)
        .build()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
        db.close()
    }

    private class FixedBase(base: String) : IBaseCurrencyRepository {
        private val flow = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = flow
        override suspend fun set(code: String) { flow.value = code }
    }

    private class FakeSyncState(initial: RateSyncState) : IRateSyncStateRepository {
        private val flow = MutableStateFlow(initial)
        override fun observe(): StateFlow<RateSyncState> = flow
        override suspend fun record(state: RateSyncState) { flow.value = state }
    }

    private class Currencies(private val inUse: List<String>) : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse, inUse.firstOrNull())
    }

    private val repository = ExchangeRateRepository(
        dao = db.exchangeRateDao(),
        mapper = ExchangeRateMapper(),
        baseCurrencyRepository = FixedBase("BRL"),
    )

    private suspend fun seed(
        currency: String,
        date: LocalDate = today,
        counterCurrency: String = "BRL",
        value: Double = 5.5,
        source: ExchangeRate.Source = ExchangeRate.Source.DERIVED,
    ) = repository.save(
        ExchangeRate(
            currency = currency,
            counterCurrency = counterCurrency,
            date = date,
            rate = value,
            source = source,
        )
    )

    private fun viewModel(
        syncState: RateSyncState = RateSyncState(),
        inUse: List<String> = listOf("BRL"),
    ) = ExchangeRatesViewModel(
        baseCurrencyRepository = FixedBase("BRL"),
        exchangeRateRepository = repository,
        rateSyncStateRepository = FakeSyncState(syncState),
        getAccountCurrencies = Currencies(inUse),
    )

    /** Every group's rows, flattened — what the per-row assertions ask about. */
    private val ExchangeRatesUiState.inForce get() = groups.flatMap { it.rates }

    private suspend fun ExchangeRatesViewModel.loaded(): ExchangeRatesUiState {
        var state = uiState.value
        uiState.test {
            state = awaitItem().takeIf { !it.isLoading } ?: awaitItem()
            cancelAndIgnoreRemainingEvents()
        }
        return state
    }

    /** The whole reason the entry view exists: thirty observations, one line. */
    @Test
    fun `one line per pair over an archive of thirty observations of it`() = runTest {
        repeat(30) { day -> seed("USD", date = today.minus(day, DateTimeUnit.DAY), value = 5.0 + day) }

        val state = viewModel().loaded()

        val line = state.inForce.single()
        assertEquals("USD", line.rate.currency)
        assertEquals("BRL", line.rate.counterCurrency)
        assertEquals(today, line.rate.date)
        assertEquals(5.0, line.rate.rate)
    }

    /** The line declares the observation that answers, origin included. */
    @Test
    fun `the line declares the pair, the value, the date and the origin`() = runTest {
        seed("USD", value = 5.5, source = ExchangeRate.Source.DERIVED)
        seed("USD", value = 5.4, source = ExchangeRate.Source.REMOTE)

        val line = viewModel().loaded().inForce.single()

        assertEquals("USD" to "BRL", line.rate.currency to line.rate.counterCurrency)
        assertEquals(5.4, line.rate.rate)
        assertEquals(today, line.rate.date)
        assertEquals(
            ExchangeRate.Source.REMOTE,
            line.rate.source,
            "the policy elects the quote over the harvest on the same date",
        )
    }

    /**
     * The entry view groups too, and by the same key the history uses. Reducing to one row
     * per pair is about *how many* rows there are; it says nothing about how they are
     * headed, and a flat list leaves the column of quotes with nothing stating what they
     * are priced in.
     */
    @Test
    fun `the rates in force are grouped by the currency they are priced in`() = runTest {
        seed("USD", counterCurrency = "BRL", date = today.minus(5, DateTimeUnit.DAY))
        seed("EUR", counterCurrency = "BRL", date = today.minus(10, DateTimeUnit.DAY))
        seed("JPY", counterCurrency = "USD", date = today)

        val state = viewModel().loaded()

        assertEquals(listOf("USD", "BRL"), state.groups.map { it.counterCurrency })
        assertEquals(2, state.groups.single { it.counterCurrency == "BRL" }.rates.size)
    }

    /** The ordinary archive is one group, which is the case the key is chosen for. */
    @Test
    fun `an archive priced entirely in the base is one group`() = runTest {
        seed("USD")
        seed("EUR")
        seed("JPY")

        val state = viewModel().loaded()

        assertEquals(listOf("BRL"), state.groups.map { it.counterCurrency })
        assertEquals(3, state.groups.single().rates.size)
    }

    @Test
    fun `a rate older than thirty days is flagged, a recent one is not`() = runTest {
        seed("USD", date = today.minus(31, DateTimeUnit.DAY))
        seed("EUR", date = today)

        val state = viewModel().loaded()

        assertTrue(state.inForce.single { it.rate.currency == "USD" }.isOutdated)
        assertFalse(state.inForce.single { it.rate.currency == "EUR" }.isOutdated)
    }

    /** Exactly thirty days is still current: the threshold is "older than". */
    @Test
    fun `the threshold is exclusive`() = runTest {
        seed("USD", date = today.minus(30, DateTimeUnit.DAY))

        assertFalse(viewModel().loaded().inForce.single().isOutdated)
    }

    @Test
    fun `an empty archive is empty rather than loading forever`() = runTest {
        val state = viewModel().loaded()

        assertTrue(state.isEmpty)
        assertEquals("BRL", state.baseCurrency)
    }

    /**
     * The instant is what distinguishes *never* from *already*, and nothing more: the
     * screen never announces the upkeep that worked, so what is asserted here is that the
     * state carries the date — not that anything renders it.
     */
    @Test
    fun `a synchronisation that happened leaves the never state behind`() = runTest {
        val yesterday = today.minus(1, DateTimeUnit.DAY)
        val state = viewModel(
            syncState = RateSyncState(
                syncedAt = mapOf(RatePair("USD", "BRL") to yesterday.atStartOfDayIn(TimeZone.currentSystemDefault())),
            )
        ).loaded()

        assertEquals(yesterday, state.sync.lastSyncedOn)
    }

    /** *Never* is a state, and deliberately not some date. */
    @Test
    fun `never having synchronised is presented as such and not as a date`() = runTest {
        assertNull(viewModel().loaded().sync.lastSyncedOn)
    }

    /**
     * Two different states with two different actions — wait, or enter it by hand — and
     * only the distinction between them is actionable.
     */
    @Test
    fun `a currency in use the source refuses is presented as not covered`() = runTest {
        seed("USD")

        val state = viewModel(
            syncState = RateSyncState(notCoveredCurrencies = setOf("ARS", "VES")),
            inUse = listOf("BRL", "USD", "ARS"),
        ).loaded()

        assertEquals(
            listOf("ARS"),
            state.sync.notCoveredCurrencies,
            "only the currencies the user actually holds have an addressee",
        )
        assertEquals(
            listOf("USD"),
            state.inForce.map { it.rate.currency },
            "and the dollar, which simply has a rate, is not one of them",
        )
    }
}
