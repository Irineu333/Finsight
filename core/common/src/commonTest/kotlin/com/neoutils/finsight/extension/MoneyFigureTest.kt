package com.neoutils.finsight.extension

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Pins the juxtaposition rule of a multi-term figure. As in [DisplayAmountTest], every
 * assertion is stated as a *relation* to `CurrencyFormatter.format`: the formatter follows
 * the platform's default locale for the format, so a currency literal would pin the machine
 * that ran the test rather than the rule.
 */
class MoneyFigureTest {

    private val formatter = CurrencyFormatter()

    private val brl = Denomination.exact("BRL")
    private val usd = Denomination.exact("USD")
    private val approximateBrl = Denomination.approximate("BRL")

    private fun formatted(value: Double, currency: String) = formatter.format(value, currency)

    @Test
    fun oneTermIsOneLineAndCarriesNoOperator() {
        val figure = MoneyFigure.of(DisplayAmount.natural(100.0, brl))

        assertEquals(listOf(formatted(100.0, "BRL")), formatter.formatTerms(figure))
        assertTrue(figure.isSingleTerm)
        assertEquals(emptyList(), figure.rest)
    }

    @Test
    fun theSecondTermIsGluedToAPlus() {
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, brl),
                DisplayAmount.natural(50.0, usd),
            )
        )

        assertEquals(
            listOf(formatted(100.0, "BRL"), "+" + formatted(50.0, "USD")),
            formatter.formatTerms(figure),
        )
    }

    @Test
    fun aTermThatSpellsItsOwnSignDoesNotGrowASecondOne() {
        val expense = MoneyFigure.of(
            listOf(
                DisplayAmount.forcedNegative(100.0, brl),
                DisplayAmount.forcedNegative(50.0, usd),
            )
        )

        assertEquals(
            listOf("-" + formatted(100.0, "BRL"), "-" + formatted(50.0, "USD")),
            formatter.formatTerms(expense),
        )

        val income = MoneyFigure.of(
            listOf(
                DisplayAmount.forcedPositive(100.0, brl),
                DisplayAmount.forcedPositive(50.0, usd),
            )
        )

        assertEquals(
            listOf("+" + formatted(100.0, "BRL"), "+" + formatted(50.0, "USD")),
            formatter.formatTerms(income),
        )
    }

    @Test
    fun aNegativeNaturalTermIsItsOwnOperator() {
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, brl),
                DisplayAmount.natural(-50.0, usd),
            )
        )

        assertEquals(
            listOf(formatted(100.0, "BRL"), formatted(-50.0, "USD")),
            formatter.formatTerms(figure),
        )
    }

    @Test
    fun theMarkQualifiesTheFigureAndSurvivesTheOperator() {
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, approximateBrl),
                DisplayAmount.natural(50.0, usd),
            )
        )

        assertTrue(figure.isApproximate)
        assertEquals(
            listOf("≈ " + formatted(100.0, "BRL"), "+" + formatted(50.0, "USD")),
            formatter.formatTerms(figure),
        )
    }

    @Test
    fun aFigureOfExactTermsIsNotApproximate() {
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, brl),
                DisplayAmount.natural(50.0, usd),
            )
        )

        assertFalse(figure.isApproximate)
    }

    @Test
    fun theFirstTermIsTheOneASurfaceWithRoomForOneKeeps() {
        val base = DisplayAmount.natural(100.0, approximateBrl)
        val own = DisplayAmount.natural(50.0, usd)
        val figure = MoneyFigure.of(listOf(base, own))

        assertEquals(base, figure.primary)
        assertEquals(listOf(own), figure.rest)
    }

    @Test
    fun `one term needs no declaration, so the single line is the plain reading`() {
        val figure = MoneyFigure.of(DisplayAmount.natural(100.0, brl))

        assertEquals(formatted(100.0, "BRL"), formatter.formatSingleLine(figure))
    }

    @Test
    fun `a single approximate term keeps its own mark, and gains no marker`() {
        val figure = MoneyFigure.of(DisplayAmount.natural(100.0, approximateBrl))

        assertEquals("≈ " + formatted(100.0, "BRL"), formatter.formatSingleLine(figure))
    }

    @Test
    fun `two exact terms on one line are marked, because one of them is not the figure`() {
        // The reachable case the forced mark exists for: no rate at all, so nothing was
        // converted and every term is exact — and one of them alone is still not the figure.
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, usd),
                DisplayAmount.natural(50.0, Denomination.exact("EUR")),
            )
        )

        assertFalse(figure.isApproximate)
        assertEquals("≈ " + formatted(100.0, "USD") + " +…", formatter.formatSingleLine(figure))
    }

    @Test
    fun `an already approximate primary is not marked twice`() {
        val figure = MoneyFigure.of(
            listOf(
                DisplayAmount.natural(100.0, approximateBrl),
                DisplayAmount.natural(50.0, usd),
            )
        )

        val line = formatter.formatSingleLine(figure)

        assertEquals("≈ " + formatted(100.0, "BRL") + " +…", line)
        assertEquals(1, line.count { it == '≈' })
    }

    @Test
    fun `no term is dropped without the line saying so`() {
        val exact = MoneyFigure.of(DisplayAmount.natural(100.0, brl))
        val twoTerms = MoneyFigure.of(
            listOf(DisplayAmount.natural(100.0, brl), DisplayAmount.natural(50.0, usd))
        )

        // The marker is there exactly when a term was left out, and never otherwise.
        assertFalse(formatter.formatSingleLine(exact).contains("+…"))
        assertTrue(formatter.formatSingleLine(twoTerms).contains("+…"))
    }

    @Test
    fun aFigureOfNoTermsIsNotBuildable() {
        assertFailsWith<IllegalArgumentException> { MoneyFigure.of(emptyList()) }
    }
}
