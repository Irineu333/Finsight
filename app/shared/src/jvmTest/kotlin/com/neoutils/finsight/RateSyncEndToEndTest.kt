package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.repository.IRateSyncStateRepository
import com.neoutils.finsight.domain.repository.IRemoteRateSource
import com.neoutils.finsight.domain.repository.RemoteQuote
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.SyncExchangeRatesUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import org.koin.core.module.Module
import org.koin.dsl.module
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.math.abs
import kotlin.test.assertTrue

/**
 * The upkeep end to end, through the real graph: a multi-currency user who has entered
 * nothing and transacted nothing ends up with rates, and the figures **add up** instead
 * of stacking terms.
 *
 * The remote source is the one thing substituted — a port whose real implementation
 * reaches outside the process. Everything else is real: the real Koin graph, the real
 * archive over a real database, the real reducer.
 */
class RateSyncEndToEndTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)

    /** The source's own publication date — a Friday, read on the following Sunday. */
    private val friday = LocalDate(2026, 3, 13)

    private class FakeRemoteSource(
        private val answers: Map<String, RemoteQuote>,
        private val coverage: Set<String>? = null,
    ) : IRemoteRateSource {
        val asked = mutableListOf<Pair<String, String>>()
        override suspend fun coverage(): Set<String>? = coverage
        override suspend fun quote(currency: String, against: String): RemoteQuote {
            asked += currency to against
            return answers[currency] ?: RemoteQuote.Unavailable
        }
    }

    private fun sourceOf(source: IRemoteRateSource): Module = module {
        single<IRemoteRateSource> { source }
    }

    /** Every account's whole balance, consolidated as of [on]. */
    private suspend fun AppLedgerHarness.netWorth(on: LocalDate) =
        get<ConsolidateMoneyUseCase>()(
            money = get<CalculateBalanceUseCase>()(march),
            on = on,
            policy = DisplayAmount::natural,
        )

    @Test
    fun `a multi-currency user gets rates without entering anything, and the figure adds up`() {
        val source = FakeRemoteSource(
            mapOf(
                "USD" to RemoteQuote.Observed(friday, 5.0),
                "EUR" to RemoteQuote.Observed(friday, 6.0),
            )
        )

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 100.0, date = day)
            income(chase, amount = 50.0, date = day)

            // No rate at all, and the figure is honest about it: one term per currency.
            val before = netWorth(on = day)
            assertEquals(2, before.terms.size, "with no rate the figure keeps one term per currency")

            get<SyncExchangeRatesUseCase>()()

            val archive = get<IExchangeRateRepository>()
            val stored = archive.observeAll().first()

            // Written in the direction it will be read — the currency priced in the base
            // — and no quotient was inverted to store it.
            val usd = stored.single { it.currency == "USD" }
            assertEquals("BRL", usd.counterCurrency)
            assertEquals(ExchangeRate.Source.REMOTE, usd.source)
            // The date the source declared, never the day the synchronisation ran.
            assertEquals(friday, usd.date)

            assertTrue("USD" to "BRL" in source.asked, "the dollar was quoted against the base")
            assertTrue(source.asked.none { it.first == "BRL" }, "the base is never quoted against itself")

            val after = netWorth(on = day)
            assertEquals(1, after.terms.size, "with a rate the figure sums instead of stacking terms")
            assertTrue(after.isApproximate, "and it says so: a conversion happened")
            assertEquals(350.0, after.terms.single().value, "100 BRL + 50 USD at 5.0")
        }
    }

    /**
     * The bug this was found by, in the shape it was found: the day's synchronisation has
     * already run, and only *then* does the user create their first account in a foreign
     * currency. Covering what the app **offers** rather than only what is in use is what
     * makes the rate be there already, instead of the figure stacking terms until the next
     * launch of the following day.
     */
    @Test
    fun `an account created after the day's run finds its rate already there`() {
        val source = FakeRemoteSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0)))

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            income(nubank, amount = 100.0, date = day)

            // The launch of a single-currency user: the dollar is quoted anyway, because
            // the app offers it.
            get<SyncExchangeRatesUseCase>()()

            val chase = account("Chase", currency = "USD")
            income(chase, amount = 50.0, date = day)

            val figure = netWorth(on = day)
            assertEquals(1, figure.terms.size, "the rate preceded the account")
            assertEquals(350.0, figure.terms.single().value)
        }
    }

    /**
     * And the half that a per-currency bound buys: a currency registered after the day's
     * run is quoted straight away, while the ones already answered are not asked twice.
     */
    @Test
    fun `a currency registered after the day's run is quoted without waiting a day`() {
        // Every currency the app offers answers, so that the only thing left to explain in
        // the second round is the one that was registered — an unavailable one would
        // legitimately be retried, which is a different rule.
        val source = FakeRemoteSource(
            listOf("USD", "EUR", "GBP", "CHF", "CNY", "JPY")
                .associateWith { RemoteQuote.Observed(friday, 5.0) }
        )

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            account("Nubank", currency = "BRL", isDefault = true)

            val sync = get<SyncExchangeRatesUseCase>()
            sync()
            val askedOnLaunch = source.asked.toList()
            assertTrue(askedOnLaunch.none { it.first == "JPY" }, "the yen is not registered yet")

            get<com.neoutils.finsight.domain.repository.ICurrencyRepository>()
                .save(code = "JPY", symbol = "¥", name = null)
            sync()

            assertEquals(
                askedOnLaunch + ("JPY" to "BRL"),
                source.asked,
                "only the currency that is new goes out to the network",
            )
            assertTrue(
                get<IExchangeRateRepository>().observeAll().first().any { it.currency == "JPY" },
                "and its rate is in the archive the same day",
            )
        }
    }

    /**
     * The archive is *everything priced in the base*, so switching the base makes a whole
     * set of pairs become the ones that answer — and none of them has ever been fetched.
     * The upkeep owes a round on the switch, and the per-pair bound is what lets it deliver
     * one the same day.
     */
    @Test
    fun `switching the base fetches the rates in the new direction, the same day`() {
        val source = FakeRemoteSource(
            listOf("USD", "EUR", "GBP", "CHF", "CNY", "BRL")
                .associateWith { RemoteQuote.Observed(friday, 5.0) }
        )

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            account("Nubank", currency = "BRL", isDefault = true)
            account("Chase", currency = "USD")

            val sync = get<SyncExchangeRatesUseCase>()
            sync()

            assertTrue(
                source.asked.none { it.first == "BRL" },
                "with a real base nothing is quoted against itself",
            )

            get<IBaseCurrencyRepository>().set("USD")
            sync()

            assertTrue(
                "BRL" to "USD" in source.asked,
                "the real priced in dollars is the row that just became the one that answers",
            )
            assertTrue(
                get<IExchangeRateRepository>().observeAll().first()
                    .any { it.currency == "BRL" && it.counterCurrency == "USD" },
                "and it is in the archive the same day, in the direction it will be read",
            )
        }
    }

    @Test
    fun `synchronising twice over the same publication leaves one observation`() {
        val source = FakeRemoteSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0)))

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            account("Nubank", currency = "BRL", isDefault = true)
            account("Chase", currency = "USD")

            val sync = get<SyncExchangeRatesUseCase>()
            sync()
            // The daily bound is the first guard; the unique key is the second, and it is
            // the one that makes the operation idempotent whatever the cadence.
            get<IRateSyncStateRepository>().record(
                get<IRateSyncStateRepository>().observe().value.copy(syncedAt = emptyMap())
            )
            sync()

            assertEquals(1, get<IExchangeRateRepository>().observeAll().first().size)
        }
    }

    /** The cross between two non-base currencies costs no request of its own. */
    @Test
    fun `a cross between two non-base currencies resolves by triangulation over the base`() {
        val source = FakeRemoteSource(
            mapOf(
                "USD" to RemoteQuote.Observed(friday, 5.0),
                "EUR" to RemoteQuote.Observed(friday, 6.0),
            )
        )

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            account("Nubank", currency = "BRL", isDefault = true)
            account("Chase", currency = "USD")
            account("N26", currency = "EUR")

            get<SyncExchangeRatesUseCase>()()

            assertTrue(
                source.asked.containsAll(listOf("USD" to "BRL", "EUR" to "BRL")),
                "both non-base currencies were quoted against the base",
            )
            assertTrue(
                source.asked.none { it.second != "BRL" },
                "no pair between two non-base currencies is ever asked for",
            )

            val cross = get<IExchangeRateRepository>().rateBetween("EUR", "USD", friday)
            assertNotNull(cross, "the cross has to be reachable")
            assertTrue(abs(6.0 / 5.0 - cross.rate) < 1e-9, "the cross is the quotient of the two legs")
            assertEquals(0, cross.id, "and it is implied, not stored")
        }
    }

    @Test
    fun `a transport failure creates and alters no observation`() {
        val source = FakeRemoteSource(mapOf("USD" to RemoteQuote.Unavailable))

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            account("Nubank", currency = "BRL", isDefault = true)
            account("Chase", currency = "USD")

            get<SyncExchangeRatesUseCase>()()

            assertEquals(emptyList(), get<IExchangeRateRepository>().observeAll().first())
            assertNull(get<IRateSyncStateRepository>().observe().value.lastSyncedAt)
        }
    }

    /**
     * The harvest goes on happening with the remote source active. It is the only origin
     * that answers offline and the only one that reaches pairs outside the coverage, so it
     * stays in the archive for the dates the remote one does not reach.
     */
    @Test
    fun `the harvest still happens on a pair the synchronisation already covers`() {
        val source = FakeRemoteSource(mapOf("USD" to RemoteQuote.Observed(friday, 5.0)))

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 1_000.0, date = day)

            get<SyncExchangeRatesUseCase>()()

            get<TransferBetweenAccountsUseCase>()(
                sourceAccountId = nubank.id,
                destinationAccountId = chase.id,
                amount = 550.0,
                date = day,
                destinationAmount = 100.0,
            ).onLeft { error("the cross-currency transfer was refused: $it") }

            val stored = get<IExchangeRateRepository>().observeAll().first()

            assertTrue(
                stored.any { it.currency == "USD" && it.source == ExchangeRate.Source.REMOTE },
                "the quote left its observation",
            )
            assertTrue(
                stored.any { it.source == ExchangeRate.Source.DERIVED },
                "and the harvest left its own, on the same pair",
            )
            // Each in the direction it was observed in, and neither inverted to join the
            // other: the quote asked for the dollar priced in reais, the operation
            // happened from reais into dollars.
            val harvested = stored.single { it.source == ExchangeRate.Source.DERIVED }
            assertEquals("BRL" to "USD", harvested.currency to harvested.counterCurrency)
            assertEquals(100.0 / 550.0, harvested.rate)
            assertEquals(day, harvested.date, "the harvest speaks about the day of the operation")

            val quoted = stored.single { it.source == ExchangeRate.Source.REMOTE }
            assertEquals("USD" to "BRL", quoted.currency to quoted.counterCurrency)
            assertEquals(friday, quoted.date, "the quote about the day it was published")
        }
    }

    /**
     * The declared limit of the guarantee (design D6): what the change buys is about the
     * user **with** network, and the first run without one falls into the behaviour that
     * was already defined — with no error shown anywhere.
     */
    @Test
    fun `a first run with no network has no rate at all, and shows no error`() {
        val source = FakeRemoteSource(emptyMap())

        runApp(baseCurrency = "BRL", overrides = sourceOf(source)) {
            val nubank = account("Nubank", currency = "BRL", isDefault = true)
            val chase = account("Chase", currency = "USD")
            income(nubank, amount = 100.0, date = day)
            income(chase, amount = 50.0, date = day)

            get<SyncExchangeRatesUseCase>()()

            assertEquals(emptyList(), get<IExchangeRateRepository>().observeAll().first())

            val figure = netWorth(on = day)
            assertEquals(
                2,
                figure.terms.size,
                "the figure keeps one term per currency, which is a defined state and not an error",
            )
        }
    }
}
