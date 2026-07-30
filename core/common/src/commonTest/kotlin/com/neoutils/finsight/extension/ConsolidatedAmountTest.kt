package com.neoutils.finsight.extension

import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The reading of a figure: one mark per figure, the `+` glued to the terms that follow,
 * and a declared term to degrade to when the surface cannot hold them all.
 */
class ConsolidatedAmountTest {

    private val formatter = CurrencyFormatter()

    @Test
    fun oneTermIsTheCommonCaseAndIsNotSpecial() {
        val figure = ConsolidatedAmount(
            terms = listOf(DisplayAmount.natural(100.0, BRL, isApproximate = false)),
            isApproximate = false,
            baseIndex = 0,
        )

        assertEquals(listOf(formatter.format(100.0, BRL)), formatter.formatTerms(figure))
    }

    /**
     * The defect this closes, seen on the statement and on the exported report: a summary
     * line is a signed figure, so gluing the joiner onto it printed `++R$ 100,00` for an
     * income and `+-US$ 50,00` for an expense.
     */
    @Test
    fun aTermThatSpellsItsOwnSignIsNotGivenAJoiner() {
        val income = ConsolidatedAmount(
            terms = listOf(
                DisplayAmount.forcedPositive(100.0, BRL, isApproximate = true),
                DisplayAmount.forcedPositive(50.0, USD, isApproximate = true),
            ),
            isApproximate = true,
            baseIndex = 0,
        )
        val expense = ConsolidatedAmount(
            terms = listOf(
                DisplayAmount.forcedNegative(100.0, BRL, isApproximate = true),
                DisplayAmount.forcedNegative(50.0, USD, isApproximate = true),
            ),
            isApproximate = true,
            baseIndex = 0,
        )

        formatter.formatTerms(income).forEach {
            assertTrue("++" !in it, "the joiner was stacked on a sign: $it")
        }
        formatter.formatTerms(expense).forEach {
            assertTrue("+-" !in it, "the joiner was stacked on a sign: $it")
        }

        // The sign it spells is the continuation: nothing is added, nothing is lost.
        assertEquals("+${formatter.format(50.0, USD)}", formatter.formatTerms(income)[1])
        assertEquals("-${formatter.format(50.0, USD)}", formatter.formatTerms(expense)[1])
    }

    /** A magnitude spells no sign, so the joiner is what marks it as a continuation. */
    @Test
    fun twoTermsAreJoinedByAPlusAndMarkedOnce() {
        val figure = approximate()

        val texts = formatter.formatTerms(figure)

        assertEquals("$APPROXIMATION_MARK ${formatter.format(100.0, BRL)}", texts[0])
        assertEquals("+${formatter.format(50.0, USD)}", texts[1])
        assertTrue(
            texts.drop(1).none { it.contains(APPROXIMATION_MARK) },
            "one figure carries one mark",
        )
    }

    @Test
    fun aNarrowSurfaceDegradesToTheBaseTerm() {
        assertEquals(BRL, approximate().degradedTerm().currency)
    }

    @Test
    fun aFigureWithNoBaseTermStillHasSomethingToDegradeTo() {
        // Two foreign currencies and no rate: nothing reduced, so there is no base term —
        // and the first one still carries the figure's mark.
        val figure = ConsolidatedAmount(
            terms = listOf(
                DisplayAmount.natural(50.0, USD, isApproximate = true),
                DisplayAmount.natural(10.0, EUR, isApproximate = true),
            ),
            isApproximate = true,
            baseIndex = null,
        )

        assertEquals(USD, figure.degradedTerm().currency)
        assertTrue(figure.degradedTerm().isApproximate)
    }

    @Test
    fun theFooterIsSilentWhenEveryFigureIsExact() {
        val exact = ConsolidatedAmount(
            terms = listOf(DisplayAmount.natural(100.0, BRL, isApproximate = false)),
            isApproximate = false,
            baseIndex = 0,
        )

        assertNull(listOf(exact, exact).approximateFigure())
        assertNull(emptyList<ConsolidatedAmount>().approximateFigure())
    }

    @Test
    fun theFooterExplainsTheDateOfTheFirstApproximateFigure() {
        val march = LocalDate(2026, 3, 31)
        val exact = ConsolidatedAmount(
            terms = listOf(DisplayAmount.natural(1.0, BRL, isApproximate = false)),
            isApproximate = false,
            baseIndex = 0,
        )

        assertEquals(march, listOf(exact, approximate(on = march)).approximateFigure()?.asOf)
    }

    private fun approximate(on: LocalDate? = null) = ConsolidatedAmount(
        terms = listOf(
            DisplayAmount.natural(100.0, BRL, isApproximate = true),
            DisplayAmount.natural(50.0, USD, isApproximate = true),
        ),
        isApproximate = true,
        baseIndex = 0,
        asOf = on,
    )

    private companion object {
        const val BRL = "BRL"
        const val USD = "USD"
        const val EUR = "EUR"
    }
}
