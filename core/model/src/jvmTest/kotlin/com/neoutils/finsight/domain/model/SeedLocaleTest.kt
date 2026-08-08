package com.neoutils.finsight.domain.model

import java.util.Locale
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The glyph a seeded row carries is the reader's, not the currency's home country's.
 *
 * The dollar is the case that decides it: `$` to someone reading in English and `US$`
 * to someone reading in Portuguese, and each is the only right answer on its own screen.
 */
class SeedLocaleTest {

    private val original = Locale.getDefault()

    @AfterTest fun restore() = Locale.setDefault(original)

    private fun symbolsIn(tag: String): Map<String, String> {
        Locale.setDefault(Locale.forLanguageTag(tag))
        return PlatformCurrencySeeding().rows().associate { it.code to it.symbol }
    }

    @Test
    fun `the dollar is written the way the reader writes it`() {
        assertEquals("$", symbolsIn("en-US")["USD"])
        assertEquals("US$", symbolsIn("pt-BR")["USD"])
    }

    @Test
    fun `the real is the real on both, because there is nothing to disambiguate`() {
        assertEquals("R$", symbolsIn("en-US")["BRL"])
        assertEquals("R$", symbolsIn("pt-BR")["BRL"])
    }

    @Test
    fun `the locale's own currency arrives once, not twice`() {
        val codes = run {
            Locale.setDefault(Locale.forLanguageTag("pt-BR"))
            PlatformCurrencySeeding().rows().map { it.code }
        }
        assertEquals(codes.distinct(), codes, "BRL is in the seed and is also the locale's")
    }
}
