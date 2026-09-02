package com.neoutils.finsight.mcp.surface

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.AccountCurrencies
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetAccountCurrenciesUseCase
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * **Every figure declares its currency, and one that could not be reduced says so.**
 *
 * A payload of `{"amount": 1234.56}` destroys the invariant the whole read surface is built on: the
 * ledger answers per currency precisely because reais and dollars do not add, and an agent handed a
 * bare number adds them in its next sentence with nothing anywhere to contradict it.
 *
 * The case that matters most is the one design D16 names: the rate archive is local and offline, so
 * *"no rate for this currency yet"* is a state the real flow requires — a user creates their first
 * foreign account and is in it immediately. The response there says what it could not do and hands
 * over the parts, rather than dropping a currency or presenting an approximation as exact.
 */
class AgentFigureTest {

    private val march = LocalDate(2026, 3, 31)

    @Test
    fun `a figure in one currency is exact, in that currency, whatever the base is`() = runTest {
        val figure = consolidator(base = "BRL").agentFigure(
            money = MoneyByCurrency.of("USD", 1_200.0),
            on = march,
            policy = DisplayAmount::natural,
        )

        assertEquals(1_200.0, figure.amount)
        assertEquals("USD", figure.currency)
        assertEquals(listOf(AgentMoney("USD", 1_200.0)), figure.byCurrency)
        assertEquals(false, figure.isApproximate)
        assertNull(figure.rateDate, "no rate took part, so there is no date to report")
        assertNull(figure.limitation)
    }

    @Test
    fun `a figure that crossed currencies carries the number, the parts and the date of the rate`() = runTest {
        val figure = consolidator(base = "BRL", rates = mapOf("USD" to 5.0)).agentFigure(
            money = MoneyByCurrency.of(mapOf("BRL" to 1_000.0, "USD" to 200.0)),
            on = march,
            policy = DisplayAmount::natural,
        )

        assertEquals(2_000.0, figure.amount)
        assertEquals("BRL", figure.currency)
        assertEquals(
            listOf(AgentMoney("BRL", 1_000.0), AgentMoney("USD", 200.0)),
            figure.byCurrency,
            "the decomposition is the ledger's own answer, not the reduction's terms",
        )
        assertEquals(true, figure.isApproximate)
        assertEquals(march, figure.rateDate)
        assertNull(figure.limitation, "everything converted; there was nothing left to explain")
    }

    @Test
    fun `a part no rate reaches is reported as a limitation, never dropped`() = runTest {
        val figure = consolidator(base = "BRL", rates = mapOf("USD" to 5.0)).agentFigure(
            money = MoneyByCurrency.of(mapOf("BRL" to 1_000.0, "USD" to 200.0, "JPY" to 30_000.0)),
            on = march,
            policy = DisplayAmount::natural,
        )

        assertEquals(2_000.0, figure.amount, "the part that could be converted")
        assertEquals("BRL", figure.currency)
        assertEquals(
            listOf(AgentMoney("BRL", 1_000.0), AgentMoney("JPY", 30_000.0), AgentMoney("USD", 200.0)),
            figure.byCurrency,
            "the yen is in the decomposition, exact, whatever the rates could not do",
        )

        val limitation = assertNotNull(figure.limitation, "a missing rate has to be said out loud")
        assertEquals(listOf("JPY"), limitation.missingRateFor)
        assertTrue("JPY" in limitation.explanation && "$march" in limitation.explanation)
    }

    @Test
    fun `with no rate at all there is no number, and the figure says so`() = runTest {
        val figure = consolidator(base = "EUR").agentFigure(
            money = MoneyByCurrency.of(mapOf("BRL" to 1_000.0, "USD" to 200.0)),
            on = march,
            policy = DisplayAmount::natural,
        )

        assertNull(figure.amount, "naming one of the two as the figure would be picking a currency")
        assertNull(figure.currency)
        assertEquals(listOf(AgentMoney("BRL", 1_000.0), AgentMoney("USD", 200.0)), figure.byCurrency)
        assertEquals(true, figure.isApproximate)
        assertNull(figure.rateDate, "no rate was applied, so naming one would name a fiction")
        assertEquals(listOf("BRL", "USD"), assertNotNull(figure.limitation).missingRateFor)
    }

    @Test
    fun `the decomposition reads in the same sign as the number beside it`() = runTest {
        // A card debt sits negative in the ledger and reads as a positive amount owed. The two
        // halves of one figure disagreeing — `1200` beside `-1200` — is a payload contradicting
        // itself, and an agent has no way to tell which half to believe.
        val figure = consolidator(base = "BRL").agentFigure(
            money = MoneyByCurrency.of("BRL", -1_200.0),
            on = march,
            policy = DisplayAmount::owed,
        )

        assertEquals(1_200.0, figure.amount)
        assertEquals(listOf(AgentMoney("BRL", 1_200.0)), figure.byCurrency)
    }

    @Test
    fun `a figure scoped to one account is exact and denominated by that account`() {
        val figure = AgentFigure.exact(amount = 830.0, currency = "USD")

        assertEquals(830.0, figure.amount)
        assertEquals("USD", figure.currency)
        assertEquals(listOf(AgentMoney("USD", 830.0)), figure.byCurrency)
        assertEquals(false, figure.isApproximate)
    }

    // ----------------------------------------------------------------------------------

    private fun consolidator(
        base: String,
        rates: Map<String, Double> = emptyMap(),
    ) = ConsolidateMoneyUseCase(
        baseCurrencyRepository = FixedBase(base),
        exchangeRateRepository = FixedRates(base, rates),
        getAccountCurrencies = NoAccounts,
    )

    private class FixedBase(base: String) : IBaseCurrencyRepository {
        private val state = MutableStateFlow(base)
        override fun observe(): StateFlow<String> = state
        override suspend fun set(code: String) = error("the surface never moves the base")
    }

    private class FixedRates(
        private val base: String,
        private val rates: Map<String, Double>,
    ) : IExchangeRateRepository {

        override suspend fun ratesAsOf(date: LocalDate): Map<String, ExchangeRate> =
            rates.mapValues { (currency, rate) ->
                ExchangeRate(
                    currency = currency,
                    counterCurrency = base,
                    date = date,
                    rate = rate,
                    source = ExchangeRate.Source.USER,
                )
            }

        override suspend fun rateAsOf(currency: String, date: LocalDate) = ratesAsOf(date)[currency]
        override suspend fun rateBetween(from: String, to: String, date: LocalDate): ExchangeRate? = null
        override fun observeAll(): Flow<List<ExchangeRate>> = flowOf(emptyList())
        override suspend fun save(rate: ExchangeRate) = error("the surface never writes a rate")
        override suspend fun remove(rate: ExchangeRate) = error("the surface never writes a rate")
        override suspend fun countNaming(currency: String): Int = 0
    }

    private object NoAccounts : GetAccountCurrenciesUseCase {
        override suspend fun invoke() = AccountCurrencies(inUse = emptyList(), ofDefaultAccount = null)
    }
}
