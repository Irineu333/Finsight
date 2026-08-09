package com.neoutils.finsight.extension

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Where the glyph over a value comes from — the registry's table, and nothing else.
 *
 * The platform is still asked about the *format*: separators, grouping and where the
 * symbol sits are properties of the language being read, and a locale legitimately knows
 * them. What it is no longer asked is **which symbol**, because its answer is about the
 * device rather than about the currency the user registered (design D10).
 */
class CurrencyFormatterSymbolTest {

    private fun <T> withLocale(locale: Locale, block: () -> T): T {
        val previous = Locale.getDefault()
        Locale.setDefault(locale)
        return try {
            block()
        } finally {
            Locale.setDefault(previous)
        }
    }

    /**
     * The case the registry's requirement is written about: the user edits the symbol of
     * a currency the platform already has an opinion on, and the value obeys the user.
     */
    @Test
    fun `an edited symbol is the one that appears over a value`() = withLocale(Locale.US) {
        val formatter = currencyFormatterOf(mapOf("USD" to "USD$"))

        assertTrue(formatter.format(100.0, "USD").contains("USD$"))
    }

    /**
     * The failure this closes. `USD` renders `US$` in `pt-BR` and `$` in `en-US` — the
     * platform answers for the *device*, so the same registered currency wore two glyphs
     * depending on the language the app was read in, and neither was the stored one.
     */
    @Test
    fun `the glyph does not move when the language does`() {
        val formatter = currencyFormatterOf(mapOf("USD" to "$"))

        val inBrazil = withLocale(Locale("pt", "BR")) { formatter.format(100.0, "USD") }
        val inTheStates = withLocale(Locale.US) { formatter.format(100.0, "USD") }

        assertTrue(inBrazil.contains("$"))
        assertTrue(inTheStates.contains("$"))
        assertTrue(inBrazil.none { it == 'U' })
    }

    /**
     * A currency outside ISO 4217 is no longer a second-class rendering. It used to print
     * as `MILHAS 100,00` — the bare code, in a shape no other value in the app had —
     * while the same currency showed `MI` in every selector.
     */
    @Test
    fun `a currency the platform does not know wears its stored symbol`() = withLocale(Locale.US) {
        val formatter = currencyFormatterOf(mapOf("MILHAS" to "MI"))

        val formatted = formatter.format(100.0, "MILHAS")

        assertTrue(formatted.contains("MI"))
        assertTrue(formatted.none { it == 'L' })
    }

    /**
     * The worst case is the code itself, and it is the same worst case
     * [Map.symbolOf] gives a selector — one rule, so a value and the name beside it
     * cannot disagree.
     */
    @Test
    fun `a currency the table says nothing about prints as its code`() = withLocale(Locale.US) {
        val formatter = currencyFormatterOf(emptyMap())

        assertTrue(formatter.format(100.0, "XPT").contains("XPT"))
    }

    /**
     * The locale keeps what is legitimately its own. Same currency, same glyph, two
     * languages: only the separators and the symbol's position move.
     *
     * The spaces are normalised because a currency pattern separates the symbol with a
     * non-breaking one, which is the locale's business and not this rule's.
     */
    @Test
    fun `the locale still decides separators`() {
        val formatter = currencyFormatterOf(mapOf("BRL" to "R$"))

        fun format(locale: Locale) =
            withLocale(locale) { formatter.format(1234.5, "BRL") }.replace(' ', ' ')

        assertEquals("R$ 1.234,50", format(Locale("pt", "BR")))
        assertEquals("R$1,234.50", format(Locale.US))
    }
}
