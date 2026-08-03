package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
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

    private class FakeRemoteSource(private val answers: Map<String, RemoteQuote>) : IRemoteRateSource {
        val asked = mutableListOf<Pair<String, String>>()
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

            // Written in the direction it will be read — the currency in use priced in
            // the base — and no quotient was inverted to store it.
            assertEquals(
                listOf("USD" to "BRL"),
                stored.map { it.currency to it.counterCurrency },
            )
            assertEquals(listOf("USD" to "BRL"), source.asked)
            assertEquals(ExchangeRate.Source.REMOTE, stored.single().source)
            // The date the source declared, never the day the synchronisation ran.
            assertEquals(friday, stored.single().date)

            val after = netWorth(on = day)
            assertEquals(1, after.terms.size, "with a rate the figure sums instead of stacking terms")
            assertTrue(after.isApproximate, "and it says so: a conversion happened")
            assertEquals(350.0, after.terms.single().value, "100 BRL + 50 USD at 5.0")
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
                get<IRateSyncStateRepository>().observe().value.copy(lastSyncedAt = null)
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

            assertEquals(
                setOf("USD" to "BRL", "EUR" to "BRL"),
                source.asked.toSet(),
                "the EUR/USD pair is never asked for",
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

            assertEquals(
                setOf(ExchangeRate.Source.REMOTE, ExchangeRate.Source.DERIVED),
                stored.map { it.source }.toSet(),
                "both writers left their observation on the same pair",
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
