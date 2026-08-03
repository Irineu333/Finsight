package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.repository.RemoteQuote
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant

/**
 * The upkeep, over fakes of the three ports: what it writes, in which direction, on which
 * date, and what it does when the source cannot answer.
 */
class SyncExchangeRatesUseCaseTest {

    private val friday = LocalDate(2026, 7, 31)

    /** 2026-08-02T12:00Z, a Sunday: the source's latest publication is Friday's. */
    private val sunday = Instant.fromEpochMilliseconds(1_785_412_800_000)
    private val monday = sunday + kotlin.time.Duration.parse("1d")

    private class FixedClock(private val instant: Instant) : Clock {
        override fun now() = instant
    }

    private class Currencies(private val inUse: List<String>) : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse, inUse.firstOrNull())
    }

    private class FixedBase(base: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) { state.value = code }
    }

    private class RecordingSource(private val answers: Map<String, RemoteQuote>) : IRemoteRateSource {
        val asked = mutableListOf<Pair<String, String>>()
        override suspend fun quote(currency: String, against: String): RemoteQuote {
            asked += currency to against
            return answers[currency] ?: RemoteQuote.Unavailable
        }
    }

    private class RecordingArchive : IExchangeRateRepository {
        val saved = mutableListOf<ExchangeRate>()
        override suspend fun rateAsOf(currency: String, date: LocalDate): ExchangeRate? = null
        override suspend fun ratesAsOf(date: LocalDate) = emptyMap<String, ExchangeRate>()
        override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll() = throw UnsupportedOperationException("no read waits on this")
        override suspend fun save(rate: ExchangeRate) { saved += rate }
        override suspend fun remove(rate: ExchangeRate) = Unit
        override suspend fun countNaming(currency: String) = 0
        override suspend fun removeAllNaming(currency: String) = Unit
    }

    private class FakeSyncState : IRateSyncStateRepository {
        private val flow = MutableStateFlow(RateSyncState())
        override fun observe(): StateFlow<RateSyncState> = flow
        override suspend fun record(state: RateSyncState) { flow.value = state }
    }

    private val archive = RecordingArchive()
    private val syncState = FakeSyncState()

    private fun useCase(
        source: IRemoteRateSource,
        inUse: List<String>,
        now: Instant = sunday,
        base: String = "BRL",
    ) = SyncExchangeRatesUseCase(
        getAccountCurrencies = Currencies(inUse),
        baseCurrencyRepository = FixedBase(base),
        remoteRateSource = source,
        exchangeRateRepository = archive,
        rateSyncStateRepository = syncState,
        clock = FixedClock(now),
        timeZone = TimeZone.UTC,
    )

    @Test
    fun `the row is written in the direction it will be read, with the source's date`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0583)))

        useCase(source, inUse = listOf("BRL", "USD")).invoke()

        assertEquals(listOf("USD" to "BRL"), source.asked, "the base is never quoted against itself")
        assertEquals(
            listOf(
                ExchangeRate(
                    currency = "USD",
                    counterCurrency = "BRL",
                    date = friday,
                    rate = 5.0583,
                    source = ExchangeRate.Source.REMOTE,
                )
            ),
            archive.saved,
            "the date is the publication's, never today's",
        )
    }

    @Test
    fun `running twice on the same day makes one round of requests`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0583)))
        val useCase = useCase(source, inUse = listOf("BRL", "USD"))

        useCase()
        useCase()

        assertEquals(1, source.asked.size)
    }

    /** The bound is a day and not a run: the next launch of the next day asks again. */
    @Test
    fun `the next day asks again`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0583)))

        useCase(source, inUse = listOf("BRL", "USD")).invoke()
        useCase(source, inUse = listOf("BRL", "USD"), now = monday).invoke()

        assertEquals(2, source.asked.size)
    }

    @Test
    fun `an unavailable source writes nothing and stamps nothing`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Unavailable))

        useCase(source, inUse = listOf("BRL", "USD")).invoke()

        assertEquals(emptyList(), archive.saved)
        assertNull(syncState.observe().value.lastSyncedAt, "the next launch has to try again")
    }

    @Test
    fun `a refused currency is recorded as not covered and the others are written anyway`() = runTest {
        val source = RecordingSource(
            mapOf(
                "USD" to RemoteQuote.Observed(friday, 5.0583),
                "ARS" to RemoteQuote.NotCovered,
            )
        )

        useCase(source, inUse = listOf("BRL", "USD", "ARS")).invoke()

        assertEquals(listOf("USD"), archive.saved.map { it.currency })
        assertEquals(setOf("ARS"), syncState.observe().value.notCoveredCurrencies)
        assertNotNull(syncState.observe().value.lastSyncedAt, "a currency not covered is not a failure")
    }

    @Test
    fun `a single-currency user asks nothing`() = runTest {
        val source = RecordingSource(emptyMap())

        useCase(source, inUse = listOf("BRL")).invoke()

        assertEquals(emptyList(), source.asked)
        assertEquals(emptyList(), archive.saved)
    }
}
