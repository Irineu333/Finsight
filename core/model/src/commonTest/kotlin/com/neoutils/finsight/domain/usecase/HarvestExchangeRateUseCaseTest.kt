package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class HarvestExchangeRateUseCaseTest {

    private val march = LocalDate(2026, 3, 10)

    private fun harvester(base: String = "BRL", rates: FakeRates = FakeRates()) =
        HarvestExchangeRateUseCase(FakeBaseCurrency(base), rates) to rates

    @Test
    fun `a crossing against the base registers the quotient of its two ends`() = runTest {
        val (harvest, rates) = harvester()

        val harvested = harvest(
            sourceAmount = -550.0,
            sourceCurrency = "BRL",
            targetAmount = 100.0,
            targetCurrency = "USD",
            date = march,
        )

        assertEquals("USD", harvested?.currency)
        assertEquals(5.5, harvested?.rate, "units of the base per one unit of the currency")
        assertEquals(march, harvested?.date)
        assertEquals(ExchangeRate.Source.DERIVED, harvested?.source)
        assertEquals(listOf(harvested), rates.saved)
    }

    @Test
    fun `the direction does not depend on which end the base is`() = runTest {
        val (harvest, _) = harvester()

        val harvested = harvest(
            sourceAmount = -100.0,
            sourceCurrency = "USD",
            targetAmount = 550.0,
            targetCurrency = "BRL",
            date = march,
        )

        assertEquals("USD", harvested?.currency)
        assertEquals(5.5, harvested?.rate)
    }

    @Test
    fun `the full quotient is stored, not the form a screen would show`() = runTest {
        val (harvest, _) = harvester()

        val harvested = harvest(-1000.0, "BRL", 183.0, "USD", march)

        assertEquals(1000.0 / 183.0, harvested?.rate)
    }

    @Test
    fun `a crossing between two non-base currencies teaches nothing`() = runTest {
        val (harvest, rates) = harvester()

        // A USD → EUR transfer under a BRL base implies no rate against the base, and
        // triangulating today's others would be a guess wearing an observation's
        // clothes. Explicit Non-Goal, not an omission.
        assertNull(harvest(-100.0, "USD", 92.0, "EUR", march))
        assertTrue(rates.saved.isEmpty())
    }

    @Test
    fun `a same-currency operation is not a crossing`() = runTest {
        val (harvest, rates) = harvester()

        assertNull(harvest(-100.0, "BRL", 100.0, "BRL", march))
        assertTrue(rates.saved.isEmpty())
    }

    @Test
    fun `a zero end teaches nothing rather than dividing by it`() = runTest {
        val (harvest, rates) = harvester()

        assertNull(harvest(-550.0, "BRL", 0.0, "USD", march))
        assertTrue(rates.saved.isEmpty())
    }

    @Test
    fun `what is harvested is a line of the archive, never a field of the operation`() = runTest {
        val (harvest, rates) = harvester()

        val harvested = harvest(-550.0, "BRL", 100.0, "USD", march)

        // The whole record of the crossing is this row. Deleting the transaction that
        // revealed it removes no rate — nothing here points back at one (design D27).
        assertEquals(1, rates.saved.size)
        assertEquals(
            ExchangeRate(currency = "USD", date = march, rate = 5.5, source = ExchangeRate.Source.DERIVED),
            harvested,
        )
    }
}
