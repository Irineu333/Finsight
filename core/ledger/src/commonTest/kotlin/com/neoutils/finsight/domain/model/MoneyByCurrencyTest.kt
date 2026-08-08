package com.neoutils.finsight.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MoneyByCurrencyTest {

    // --- plus: each currency summed with its own, no conversion (task 4.1) ---

    @Test
    fun `overlapping currencies are summed with their own`() {
        val sum = MoneyByCurrency.of(mapOf("BRL" to 1.0, "USD" to 2.0)) +
            MoneyByCurrency.of("USD", 3.0)

        assertEquals(1.0, sum["BRL"])
        assertEquals(5.0, sum["USD"])
        assertEquals(setOf("BRL", "USD"), sum.currencies)
    }

    @Test
    fun `disjoint currencies are kept side by side`() {
        val sum = MoneyByCurrency.of("BRL", 10.0) + MoneyByCurrency.of("EUR", 4.0)

        assertEquals(listOf("BRL", "EUR"), sum.toList().map { it.currency })
        assertEquals(10.0, sum["BRL"])
        assertEquals(4.0, sum["EUR"])
    }

    @Test
    fun `zero is the identity of the sum on both sides`() {
        val figure = MoneyByCurrency.of(mapOf("BRL" to 7.0, "USD" to 1.5))

        assertEquals(figure, figure + MoneyByCurrency.zero)
        assertEquals(figure, MoneyByCurrency.zero + figure)
        assertEquals(MoneyByCurrency.zero, MoneyByCurrency.zero + MoneyByCurrency.zero)
    }

    @Test
    fun `opposite values leave the currency present at zero`() {
        // Not normalized away: "there is movement in reais and it sums to zero" is a
        // different fact from "there is no movement", and the consolidation layer
        // needs the currency to denominate the figure (design D9).
        val sum = MoneyByCurrency.of("BRL", 100.0) + MoneyByCurrency.of("BRL", -100.0)

        assertEquals(0.0, sum["BRL"])
        assertTrue(sum.isNotEmpty)
    }

    // --- the empty figure: absence, not zero ---

    @Test
    fun `the empty figure says nothing about any currency`() {
        assertTrue(MoneyByCurrency.zero.isEmpty)
        assertNull(MoneyByCurrency.zero["BRL"])
        assertEquals(emptySet(), MoneyByCurrency.zero.currencies)
        assertEquals(emptyList(), MoneyByCurrency.zero.toList())
    }

    @Test
    fun `an absent currency is not a zero`() {
        val figure = MoneyByCurrency.of("BRL", 50.0)

        assertNull(figure["USD"])
        assertEquals(50.0, figure["BRL"])
    }

    // --- singleOrNull: the reduction a facade guarantee justifies ---

    @Test
    fun `a figure of one term reduces to that term`() {
        assertEquals(
            CurrencyAmount("USD", 42.0),
            MoneyByCurrency.of("USD", 42.0).singleOrNull(),
        )
    }

    @Test
    fun `a figure of two terms does not reduce`() {
        assertNull(MoneyByCurrency.of(mapOf("BRL" to 1.0, "USD" to 2.0)).singleOrNull())
    }

    @Test
    fun `the empty figure does not reduce`() {
        assertNull(MoneyByCurrency.zero.singleOrNull())
    }

    // --- terms are listed in a stable order, whatever the row order was ---

    @Test
    fun `terms are ordered by currency code`() {
        val figure = MoneyByCurrency.of(mapOf("USD" to 1.0, "BRL" to 2.0, "EUR" to 3.0))

        assertEquals(listOf("BRL", "EUR", "USD"), figure.toList().map { it.currency })
    }

    @Test
    fun `two figures of the same terms are equal whatever the order they were built in`() {
        assertEquals(
            MoneyByCurrency.of(mapOf("USD" to 1.0, "BRL" to 2.0)),
            MoneyByCurrency.of(mapOf("BRL" to 2.0, "USD" to 1.0)),
        )
    }
}
