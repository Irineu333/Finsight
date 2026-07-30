package com.neoutils.finsight.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CurrencyBalanceTest {

    @Test
    fun `zero names no currency at all`() {
        assertTrue(CurrencyBalance.zero.isEmpty)
        assertEquals(emptySet(), CurrencyBalance.zero.currencies)
        // Not "zero of the base currency": the ledger has no opinion on which one that is.
        assertEquals(0.0, CurrencyBalance.zero["BRL"])
    }

    @Test
    fun `a currency this figure has none of reads zero`() {
        val balance = CurrencyBalance.of("BRL", 100.0)

        assertEquals(100.0, balance["BRL"])
        assertEquals(0.0, balance["USD"])
        assertEquals(setOf("BRL"), balance.currencies)
    }

    @Test
    fun `summing disjoint currencies keeps them apart`() {
        val sum = CurrencyBalance.of("BRL", 100.0) + CurrencyBalance.of("USD", 50.0)

        assertEquals(setOf("BRL", "USD"), sum.currencies)
        assertEquals(100.0, sum["BRL"])
        assertEquals(50.0, sum["USD"])
    }

    @Test
    fun `summing the same currency adds it to its own`() {
        val sum = CurrencyBalance.of("BRL", 100.0) + CurrencyBalance.of("BRL", 25.0)

        assertEquals(setOf("BRL"), sum.currencies)
        assertEquals(125.0, sum["BRL"])
    }

    @Test
    fun `summing over an empty figure changes nothing`() {
        val balance = CurrencyBalance.of("USD", 50.0)

        assertEquals(balance, balance + CurrencyBalance.zero)
        assertEquals(balance, CurrencyBalance.zero + balance)
        assertEquals(CurrencyBalance.zero, CurrencyBalance.zero + CurrencyBalance.zero)
    }

    @Test
    fun `the sum of two perimeters is the sum of each currency, with no conversion`() {
        // The dashboard's neutral perimeter: ASSET plus LIABILITY, both read per
        // currency. Nothing here needs a rate, and nothing here could apply one.
        val assets = CurrencyBalance.of(mapOf("BRL" to 1_000.0, "USD" to 200.0))
        val liabilities = CurrencyBalance.of(mapOf("BRL" to -300.0, "EUR" to -50.0))

        val net = assets + liabilities

        assertEquals(700.0, net["BRL"])
        assertEquals(200.0, net["USD"])
        assertEquals(-50.0, net["EUR"])
    }

    @Test
    fun `two figures of the same amounts are the same figure`() {
        assertEquals(CurrencyBalance.of("BRL", 1.0), CurrencyBalance.of(mapOf("BRL" to 1.0)))
        assertEquals(CurrencyBalance.zero, CurrencyBalance.of(emptyMap()))
    }
}
