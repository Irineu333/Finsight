package com.neoutils.finsight

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.coroutines.flow.first
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * **Switching the base currency is a read, end to end.**
 *
 * The five claims the spec makes about the switch are only reachable through the whole
 * graph — a real database, a real chart of accounts in three currencies, the real
 * archive and the real reducer. A fake would answer whatever it was seeded with, which
 * is exactly the question:
 *
 * 1. it happens without migration and without reprocessing;
 * 2. no row of the archive is created, altered or removed;
 * 3. figures of past periods re-express in the new base, by inverse and triangulation,
 *    retroactively;
 * 4. switching to a currency the archive cannot reach is not prevented, and degrades
 *    into per-currency terms;
 * 5. switching back gives exactly the figures of before, because nothing was converted,
 *    stored or lost.
 */
class BaseCurrencySwitchEndToEndTest {

    private val march = YearMonth(2026, 3)
    private val day = LocalDate(2026, 3, 15)
    private val later = LocalDate(2026, 7, 20)

    /** Every account's whole balance, consolidated as of [on]. */
    private suspend fun AppLedgerHarness.netWorth(on: LocalDate) =
        get<ConsolidateMoneyUseCase>()(
            money = get<CalculateBalanceUseCase>()(march),
            on = on,
            policy = DisplayAmount::natural,
        )

    private suspend fun AppLedgerHarness.seed() {
        val nubank = account("Nubank", currency = "BRL", isDefault = true)
        val chase = account("Chase", currency = "USD")
        val n26 = account("N26", currency = "EUR")

        income(nubank, amount = 1_000.0, date = day)
        income(chase, amount = 100.0, date = day)
        income(n26, amount = 50.0, date = day)

        // The archive as a user of a BRL base would have built it.
        get<IExchangeRateRepository>().save(
            ExchangeRate(
                currency = "USD",
                counterCurrency = "BRL",
                date = day,
                rate = 5.5,
                source = ExchangeRate.Source.USER,
            )
        )
        get<IExchangeRateRepository>().save(
            ExchangeRate(
                currency = "EUR",
                counterCurrency = "BRL",
                date = day,
                rate = 6.0,
                source = ExchangeRate.Source.USER,
            )
        )
    }

    private fun assertClose(expected: Double, actual: Double) {
        assertTrue(abs(expected - actual) < 0.02, "expected ~$expected but was $actual")
    }

    @Test
    fun `switching re-expresses every figure and touches no stored row`() =
        runApp(baseCurrency = "BRL") {
            seed()

            val archiveBefore = get<IExchangeRateRepository>().observeAllOnce()

            // 1_000 + 100 × 5,5 + 50 × 6 = 1_850 reais.
            val inReais = netWorth(later)
            assertEquals(1, inReais.terms.size, "the archive reaches every currency")
            assertClose(1_850.0, inReais.terms.single().value)

            get<IBaseCurrencyRepository>().set("USD")

            // The same money, said in dollars: the real by the inverse of a stored rate,
            // the euro by triangulation over it. 1_850 ÷ 5,5 ≈ 336,36.
            val inDollars = netWorth(later)
            assertEquals(1, inDollars.terms.size)
            assertEquals("USD", inDollars.terms.single().currency)
            assertClose(1_850.0 / 5.5, inDollars.terms.single().value)

            // No migration, no reprocessing, and — the assertion the whole design rests
            // on — not one row of the archive moved.
            assertEquals(archiveBefore, get<IExchangeRateRepository>().observeAllOnce())
        }

    /**
     * Retroactively, and by the rate of the period rather than the current one: a past
     * month's figure does not move on its own when a later rate is registered.
     */
    @Test
    fun `figures of past periods re-express in the new base too`() =
        runApp(baseCurrency = "BRL") {
            seed()

            get<IBaseCurrencyRepository>().set("USD")

            val onTheDay = netWorth(day)

            assertEquals("USD", onTheDay.terms.single().currency)
            assertClose(1_850.0 / 5.5, onTheDay.terms.single().value)
        }

    /**
     * Not prevented, not warned about, and nothing demanded in the flow (design D6). The
     * figure degrades into per-currency terms, which is the behaviour already defined for
     * an absent rate — the switch only reaches it through another door.
     */
    @Test
    fun `switching to a currency the archive cannot reach degrades rather than failing`() =
        runApp(baseCurrency = "BRL") {
            seed()

            get<IBaseCurrencyRepository>().set("JPY")

            val degraded = netWorth(later)

            assertEquals(
                setOf("BRL", "USD", "EUR"),
                degraded.terms.map { it.currency }.toSet(),
                "every parcel keeps a term of its own rather than being invented into JPY",
            )
        }

    /** Nothing was converted, stored or lost, so the way back is exact. */
    @Test
    fun `switching back gives exactly the figures of before`() =
        runApp(baseCurrency = "BRL") {
            seed()

            val before = netWorth(later)

            get<IBaseCurrencyRepository>().set("USD")
            get<IBaseCurrencyRepository>().set("JPY")
            get<IBaseCurrencyRepository>().set("BRL")

            assertEquals(before, netWorth(later))
        }
}

private suspend fun IExchangeRateRepository.observeAllOnce() = observeAll().first()
