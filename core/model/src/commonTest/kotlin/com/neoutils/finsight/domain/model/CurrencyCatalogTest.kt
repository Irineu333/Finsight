package com.neoutils.finsight.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The half of resolving the device locale that this module owns: reducing whatever
 * the platform said to a currency the app is actually willing to denominate an
 * account in.
 *
 * The raw read — that the *region* decides and the language does not — is pinned in
 * `:core:common`, beside the reader. Two jobs, two owners: that module knows what the
 * device says, this one knows what the app accepts.
 */
class CurrencyCatalogTest {

    @Test
    fun `an offered currency survives the reduction unchanged`() {
        assertEquals("USD", CurrencyCatalog.reduce("USD"))
        assertEquals("BRL", CurrencyCatalog.reduce("BRL"))
    }

    @Test
    fun `a currency the app does not offer falls back rather than being invented`() {
        // JPY has no decimal places and KWD has three, so both are outside the
        // deliberate two-place premise the whole arithmetic of the app rests on.
        assertEquals(CurrencyCatalog.FALLBACK_CURRENCY, CurrencyCatalog.reduce("JPY"))
        assertEquals(CurrencyCatalog.FALLBACK_CURRENCY, CurrencyCatalog.reduce("KWD"))
    }

    @Test
    fun `no answer from the platform still yields a currency`() {
        assertEquals(CurrencyCatalog.FALLBACK_CURRENCY, CurrencyCatalog.reduce(null))
    }

    @Test
    fun `the fallback is itself offered`() {
        // Otherwise the currency of last resort would be one no selector shows.
        assertTrue(CurrencyCatalog.of(CurrencyCatalog.FALLBACK_CURRENCY) != null)
    }

    @Test
    fun `codes are unique and looked up case-insensitively`() {
        assertEquals(
            CurrencyCatalog.currencies.size,
            CurrencyCatalog.currencies.map { it.code }.toSet().size,
        )
        assertEquals("BRL", CurrencyCatalog.of("brl")?.code)
        assertNull(CurrencyCatalog.of("XXX"))
    }

    @Test
    fun `every offered currency has a glyph a form can render`() {
        assertTrue(CurrencyCatalog.currencies.none { it.symbol.isBlank() })
        assertEquals("R$", CurrencyCatalog.symbolOf("BRL"))
        // An unknown code degrades to itself rather than to a wrong glyph.
        assertEquals("XXX", CurrencyCatalog.symbolOf("XXX"))
    }
}
