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

    private fun harvester(rates: FakeRates = FakeRates()) =
        HarvestExchangeRateUseCase(rates) to rates

    @Test
    fun `a crossing registers the quotient of its two ends`() = runTest {
        val (harvest, rates) = harvester()

        val harvested = harvest(
            sourceAmount = -550.0,
            sourceCurrency = "BRL",
            targetAmount = 100.0,
            targetCurrency = "USD",
            date = march,
        )

        assertEquals("BRL", harvested?.currency)
        assertEquals("USD", harvested?.counterCurrency)
        assertEquals(100.0 / 550.0, harvested?.rate, "units of the target per one unit of the source")
        assertEquals(march, harvested?.date)
        assertEquals(ExchangeRate.Source.DERIVED, harvested?.source)
        assertEquals(listOf(harvested), rates.saved)
    }

    /**
     * The direction is the operation's, and it is never canonicalised (design D2):
     * inverting to store would keep a number nobody measured.
     */
    @Test
    fun `the direction is the one the operation happened in`() = runTest {
        val (harvest, _) = harvester()

        val harvested = harvest(
            sourceAmount = -100.0,
            sourceCurrency = "USD",
            targetAmount = 550.0,
            targetCurrency = "BRL",
            date = march,
        )

        assertEquals("USD", harvested?.currency)
        assertEquals("BRL", harvested?.counterCurrency)
        assertEquals(5.5, harvested?.rate)
    }

    @Test
    fun `the full quotient is stored, not the form a screen would show`() = runTest {
        val (harvest, _) = harvester()

        val harvested = harvest(-183.0, "USD", 1000.0, "BRL", march)

        assertEquals(1000.0 / 183.0, harvested?.rate)
    }

    /**
     * The guard that used to sit here is gone, and its removal is the point: it was
     * never a rule of the domain, only the consequence of a row that could not say which
     * pair it spoke about.
     */
    @Test
    fun `a crossing between two non-base currencies also teaches`() = runTest {
        val (harvest, rates) = harvester()

        val harvested = harvest(-100.0, "USD", 92.0, "EUR", march)

        assertEquals("USD", harvested?.currency)
        assertEquals("EUR", harvested?.counterCurrency)
        assertEquals(0.92, harvested?.rate)
        assertEquals(listOf(harvested), rates.saved)
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

        assertNull(harvest(0.0, "BRL", 100.0, "USD", march))
        assertTrue(rates.saved.isEmpty())
    }

    @Test
    fun `what is harvested is a line of the archive, never a field of the operation`() = runTest {
        val (harvest, rates) = harvester()

        val harvested = harvest(-100.0, "USD", 550.0, "BRL", march)

        // The whole record of the crossing is this row. Deleting the transaction that
        // revealed it removes no rate — nothing here points back at one (design D27).
        assertEquals(1, rates.saved.size)
        assertEquals(
            ExchangeRate(currency = "USD", counterCurrency = "BRL", date = march, rate = 5.5, source = ExchangeRate.Source.DERIVED),
            harvested,
        )
    }
}
