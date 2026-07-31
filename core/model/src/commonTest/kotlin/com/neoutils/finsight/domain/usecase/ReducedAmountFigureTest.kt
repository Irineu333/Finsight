package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CurrencyAmount
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A partial reduction seen as a figure — what a surface with room shows instead of `***`.
 *
 * The number and the figure answer different questions and both are right. A bar and a
 * "spent / limit" label need one number against one limit, and there is none when part of
 * the money cannot be priced. A detail view has room for the truth: the money *is* known,
 * only its expression in the target currency is not.
 */
class ReducedAmountFigureTest {

    @Test
    fun `a full reduction is one term, in the target currency`() {
        val figure = ReducedAmount(value = 375.0, isApproximate = true, hasUnconvertedPart = false)
            .asFigure(target = "BRL")

        assertEquals(1, figure.terms.size)
        assertEquals("BRL", figure.terms.single().currency)
        assertTrue(figure.isApproximate, "a rate took part, so the figure is not exact")
        assertEquals(0, figure.baseIndex)
    }

    @Test
    fun `what no rate reached stands beside the part that converted`() {
        val figure = ReducedAmount(
            value = 400.0,
            isApproximate = true,
            hasUnconvertedPart = true,
            unconverted = listOf(CurrencyAmount("JPY", 5000.0)),
        ).asFigure(target = "BRL")

        assertEquals(listOf("BRL", "JPY"), figure.terms.map { it.currency })
        assertEquals(400.0, figure.terms.first().value)
        assertEquals(5000.0, figure.terms.last().value)
        assertFalse(
            figure.terms.last().isApproximate,
            "a term no rate touched is the ledger's own amount — marking it claims doubt about a known number",
        )
    }

    /**
     * The zero that must not be asserted: with nothing priced, `value` is 0 and a
     * `R$ 0,00` beside `¥ 5.000` would claim a measurement nobody took.
     */
    @Test
    fun `nothing converted means no term in the target currency`() {
        val figure = ReducedAmount(
            value = 0.0,
            isApproximate = true,
            hasUnconvertedPart = true,
            unconverted = listOf(CurrencyAmount("JPY", 5000.0)),
        ).asFigure(target = "BRL")

        assertEquals(listOf("JPY"), figure.terms.map { it.currency })
        assertNull(figure.baseIndex, "there is no term in the target to degrade to")
    }

    /** A genuine zero, fully reduced, is still a figure that reads. */
    @Test
    fun `an empty reduction is a zero in the target currency`() {
        val figure = ReducedAmount(value = 0.0, isApproximate = false, hasUnconvertedPart = false)
            .asFigure(target = "USD")

        assertEquals(listOf("USD"), figure.terms.map { it.currency })
        assertEquals(0.0, figure.terms.single().value)
        assertFalse(figure.isApproximate)
    }

    /**
     * The split this figure exists to keep: **the figure** is approximate because it holds
     * parts that do not add up, and **no term** is, because no rate touched either of them.
     * Collapsing the two marks an exact number — R$ 400 that were always in reais.
     */
    @Test
    fun `a figure of parts no rate touched is approximate without any term being`() {
        val figure = ReducedAmount(
            value = 400.0,
            isApproximate = false,
            hasUnconvertedPart = true,
            unconverted = listOf(CurrencyAmount("JPY", 5000.0)),
        ).asFigure(target = "BRL")

        assertTrue(figure.isApproximate, "it is not one number, and no single number answers for it")
        assertTrue(
            figure.terms.none { it.isApproximate },
            "neither part went through a rate, so neither wears the mark",
        )
    }
}
