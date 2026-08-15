package com.neoutils.finsight.mcp.contract

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyPayloadTest {

    private val on = LocalDate(2026, 2, 1)

    @Test
    fun `a single currency is still a collection`() = runTest {
        val payload = factory(base = "BRL").spanning(
            money = MoneyByCurrency.of("BRL", 1_234.56),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        assertEquals(1, payload.amounts.size)
        assertEquals(MoneyAmount("BRL", 123_456), payload.amounts.single())
    }

    @Test
    fun `an amount crosses as minor units and scale`() = runTest {
        val payload = factory(base = "BRL").spanning(
            money = MoneyByCurrency.of("BRL", 10.0),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        val amount = payload.amounts.single()
        assertEquals(1_000, amount.minorUnits)
        assertEquals(MONEY_SCALE, amount.scale)
        assertNull(amount.formattedForDisplayOnly)
    }

    @Test
    fun `a missing rate does not become a number`() = runTest {
        val payload = factory(base = "BRL", rates = emptyMap()).spanning(
            money = MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        val consolidated = assertIs<ConsolidatedMoney.Unavailable>(payload.consolidated)
        assertEquals(ConsolidationGap.MISSING_RATE, consolidated.reason)
        assertTrue(consolidated.message.contains("USD"), consolidated.message)

        // The per-currency figure stays complete — nothing was dropped for want of a rate.
        assertEquals(
            listOf(MoneyAmount("BRL", 10_000), MoneyAmount("USD", 5_000)),
            payload.amounts,
        )
    }

    @Test
    fun `the consolidated value carries the rate, its date and its staleness`() = runTest {
        val rate = ExchangeRate(
            currency = "USD",
            counterCurrency = "BRL",
            date = LocalDate(2026, 1, 10),
            rate = 5.0,
            source = ExchangeRate.Source.REMOTE,
        )

        val payload = factory(base = "BRL", rates = mapOf("USD" to rate)).spanning(
            money = MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        val consolidated = assertIs<ConsolidatedMoney.Available>(payload.consolidated)
        assertEquals(MoneyAmount("BRL", 35_000), consolidated.amount)
        assertEquals(on, consolidated.asOf)
        assertEquals(
            listOf(AppliedRate("USD", "BRL", 5.0, LocalDate(2026, 1, 10), isStale = true)),
            consolidated.appliedRates,
        )
        assertTrue(consolidated.isStale)

        // The per-currency figure is a sibling, never replaced by the consolidated one.
        assertEquals(2, payload.amounts.size)
    }

    @Test
    fun `a rate observed on the reference date is not stale`() = runTest {
        val rate = ExchangeRate(
            currency = "USD",
            counterCurrency = "BRL",
            date = on,
            rate = 5.0,
            source = ExchangeRate.Source.REMOTE,
        )

        val payload = factory(base = "BRL", rates = mapOf("USD" to rate)).spanning(
            money = MoneyByCurrency.of(mapOf("BRL" to 100.0, "USD" to 50.0)),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        val consolidated = assertIs<ConsolidatedMoney.Available>(payload.consolidated)
        assertTrue(!consolidated.isStale)
        assertTrue(!consolidated.appliedRates.single().isStale)
    }

    @Test
    fun `a figure no rate took part in names no rate`() = runTest {
        val payload = factory(base = "BRL").spanning(
            money = MoneyByCurrency.of("BRL", 100.0),
            sign = DisplaySign.ofMoneyHeld,
            on = on,
        )

        val consolidated = assertIs<ConsolidatedMoney.Available>(payload.consolidated)
        assertNull(consolidated.asOf)
        assertEquals(emptyList(), consolidated.appliedRates)
        assertTrue(!consolidated.isStale)
    }

    @Test
    fun `expense reads negative and income reads positive`() = runTest {
        val money = factory(base = "BRL")

        // The ledger is debit-positive: an expense sums positive there, an income negative.
        val expense = money.spanning(
            money = MoneyByCurrency.of("BRL", 250.0),
            sign = DisplaySign.of(AccountType.EXPENSE),
            on = on,
        )
        val income = money.spanning(
            money = MoneyByCurrency.of("BRL", -800.0),
            sign = DisplaySign.of(AccountType.INCOME),
            on = on,
        )

        assertEquals(-25_000, expense.amounts.single().minorUnits)
        assertEquals(80_000, income.amounts.single().minorUnits)
    }

    @Test
    fun `a balance held reads with the sign the user expects`() = runTest {
        val scoped = factory(base = "BRL").scoped(
            value = 1_500.0,
            currency = "BRL",
            sign = DisplaySign.of(AccountType.ASSET),
        )

        assertEquals(MoneyAmount("BRL", 150_000), scoped)
    }

    private fun factory(
        base: String,
        rates: Map<String, ExchangeRate> = emptyMap(),
    ): MoneyPayloadFactory {
        val baseCurrency = FakeBaseCurrencyRepository(base)
        val exchangeRates = FakeExchangeRateRepository(rates)
        return MoneyPayloadFactory(
            consolidateMoney = ConsolidateMoneyUseCase(
                baseCurrencyRepository = baseCurrency,
                exchangeRateRepository = exchangeRates,
                getAccountCurrencies = FakeGetAccountCurrencies(listOf(base)),
            ),
            exchangeRates = exchangeRates,
        )
    }
}

private class FakeBaseCurrencyRepository(code: String) : IBaseCurrencyRepository {
    private val state = MutableStateFlow(code)
    override fun observe(): StateFlow<String> = state
    override suspend fun set(code: String) {
        state.value = code
    }
}

private class FakeGetAccountCurrencies(private val inUse: List<String>) : GetAccountCurrenciesUseCase {
    override suspend fun invoke() = AccountCurrencies(inUse = inUse, ofDefaultAccount = inUse.firstOrNull())
}

private class FakeExchangeRateRepository(
    private val rates: Map<String, ExchangeRate>,
) : IExchangeRateRepository {

    override suspend fun rateAsOf(currency: String, date: LocalDate) = rates[currency]

    override suspend fun ratesAsOf(date: LocalDate) = rates

    override suspend fun rateBetween(from: String, to: String, date: LocalDate) = rates[from]

    override fun observeAll() = throw UnsupportedOperationException()

    override suspend fun save(rate: ExchangeRate) = throw UnsupportedOperationException()

    override suspend fun remove(rate: ExchangeRate) = throw UnsupportedOperationException()

    override suspend fun countNaming(currency: String) = 0

    override suspend fun removeAllNaming(currency: String) = throw UnsupportedOperationException()
}
