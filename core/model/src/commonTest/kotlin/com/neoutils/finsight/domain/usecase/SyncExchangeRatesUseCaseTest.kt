package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RateSyncState
import com.neoutils.finsight.domain.repository.RemoteQuote
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
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

    /** The registry: [getOffered] is what the app offers, the archived ones already out. */
    private class FakeRegistry(private val offered: List<String>) : ICurrencyRepository {
        private val rows = offered.map { CurrencyInfo(code = it, symbol = it, name = null) }
        override fun observeOffered(): Flow<List<CurrencyInfo>> = flowOf(rows)
        override fun observeAll(): Flow<List<CurrencyInfo>> = flowOf(rows)
        override suspend fun getOffered() = rows
        override suspend fun getAll() = rows
        override suspend fun get(code: String) = rows.firstOrNull { it.code == code }
        override suspend fun exists(code: String) = code in offered
        override suspend fun save(code: String, symbol: String, name: String?) = Unit
        override suspend fun archive(code: String) = Unit
        override suspend fun unarchive(code: String) = Unit
        override suspend fun delete(code: String) = Unit
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
        offered: List<String> = inUse,
        now: Instant = sunday,
        base: String = "BRL",
    ) = SyncExchangeRatesUseCase(
        currencyRepository = FakeRegistry(offered),
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

        useCase(source, inUse = listOf("BRL"), offered = listOf("BRL")).invoke()

        assertEquals(emptyList(), source.asked)
        assertEquals(emptyList(), archive.saved)
    }

    /**
     * The whole point of covering what is *offered*: the rate has to be in the archive
     * **before** an account needs it, or every first account in a currency is born in the
     * worst case and stays there until the next day.
     */
    @Test
    fun `a currency the app offers is covered before any account uses it`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0583)))

        useCase(source, inUse = listOf("BRL"), offered = listOf("BRL", "USD")).invoke()

        assertEquals(listOf("USD" to "BRL"), source.asked)
        assertEquals(listOf("USD"), archive.saved.map { it.currency })
    }

    /**
     * Archiving is about what is offered, not about what is known — and the figure of an
     * account that survived the archiving still needs its rate.
     */
    @Test
    fun `an archived currency with a live account stays covered`() = runTest {
        val source = RecordingSource(mapOf("JPY" to RemoteQuote.Observed(friday, 0.037)))

        // Archived, so out of `offered`; still held, so in `inUse`.
        useCase(source, inUse = listOf("BRL", "JPY"), offered = listOf("BRL")).invoke()

        assertEquals(listOf("JPY" to "BRL"), source.asked)
    }

    /** And an archived currency nobody holds any more is not asked about. */
    @Test
    fun `an archived currency with no account is not covered`() = runTest {
        val source = RecordingSource(emptyMap())

        useCase(source, inUse = listOf("BRL"), offered = listOf("BRL")).invoke()

        assertEquals(emptyList(), source.asked)
    }

    /**
     * The bug this group exists for: the day's run already happened, a currency is
     * registered, and it must not have to wait until tomorrow. The bound is per currency,
     * so the new one is asked and the old ones are not asked twice.
     */
    @Test
    fun `a currency registered after the day's run is asked, and the others are not asked again`() = runTest {
        val source = RecordingSource(
            mapOf(
                "USD" to RemoteQuote.Observed(friday, 5.0583),
                "JPY" to RemoteQuote.Observed(friday, 0.037),
            )
        )

        useCase(source, inUse = listOf("BRL"), offered = listOf("BRL", "USD")).invoke()
        assertEquals(listOf("USD" to "BRL"), source.asked)

        // The registry gains JPY, the same day.
        useCase(source, inUse = listOf("BRL"), offered = listOf("BRL", "USD", "JPY")).invoke()

        assertEquals(
            listOf("USD" to "BRL", "JPY" to "BRL"),
            source.asked,
            "only the new currency goes out to the network",
        )
    }

    /**
     * A refusal is a definitive answer, so it stamps the instant like any other. Not
     * stamping it would have that currency asked again on every single launch, for ever,
     * to be told the same thing.
     */
    @Test
    fun `a refused currency is not asked again the same day`() = runTest {
        val source = RecordingSource(mapOf("ARS" to RemoteQuote.NotCovered))

        val currencies = listOf("BRL", "ARS")
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()

        assertEquals(1, source.asked.size)
        assertEquals(setOf("ARS"), syncState.observe().value.notCoveredCurrencies)
    }

    /** An unavailable one is asked again, because nothing was learned about it. */
    @Test
    fun `an unavailable currency is asked again on the next run of the same day`() = runTest {
        val source = RecordingSource(mapOf("USD" to RemoteQuote.Unavailable))

        val currencies = listOf("BRL", "USD")
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()

        assertEquals(2, source.asked.size)
    }

    /** One currency failing does not stop, or un-stamp, the ones that answered. */
    @Test
    fun `a partial round keeps what answered and retries only what did not`() = runTest {
        val source = RecordingSource(
            mapOf(
                "USD" to RemoteQuote.Observed(friday, 5.0583),
                "EUR" to RemoteQuote.Unavailable,
            )
        )

        val currencies = listOf("BRL", "USD", "EUR")
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()
        source.asked.clear()
        useCase(source, inUse = listOf("BRL"), offered = currencies).invoke()

        assertEquals(listOf("EUR" to "BRL"), source.asked)
    }
}
