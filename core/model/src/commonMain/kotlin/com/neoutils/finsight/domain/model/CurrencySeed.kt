package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.isTwoDecimalCurrency
import com.neoutils.finsight.extension.localeCurrencyCode
import com.neoutils.finsight.extension.platformCurrency

/**
 * The currency of **last resort** — not a default.
 *
 * It answers only when the device names no currency at all, or names one without two
 * decimal places, which are exactly the cases the seeding skips. It is declared here
 * because it belongs to [CURRENCY_SEED] by obligation: a last resort pointing at a row
 * that may not exist is the one way resolving the base currency could have no answer.
 */
const val FALLBACK_CURRENCY: String = "USD"

/** One row the seeding writes: the code, and the glyph that goes over a value. */
data class SeedCurrency(val code: String, val symbol: String)

/**
 * The currencies the app brings before any user data exists.
 *
 * **The criterion, so it is not re-litigated:** *the app's home market, plus the
 * currencies a figure is legible in to somebody outside it*. The five after BRL are the
 * most transacted in the world, minus JPY, which the base-100 premise already bars.
 *
 * It is deliberately **not** an attempt to cover the markets the app is used in. The
 * user's own currency arrives through their locale's row, not through this list — which
 * is what made shrinking the old curated catalog cheap instead of a regression.
 *
 * The currency of **last resort** belongs here by obligation: without it the fallback
 * would point at a row that may not exist, which is the only way resolving the base
 * currency could have no answer.
 *
 * **Codes, not glyphs.** Which currencies the app brings is a decision; how a currency
 * is written is not one this list gets to make, because it differs by who is reading:
 * the dollar is `$` to someone reading in English and `US$` to someone reading in
 * Portuguese, and both are right on their own screen. The glyph therefore comes from
 * the reader's locale, through [CurrencySeeding.symbolOf] — the same source the
 * currencies found *in use* already resolve through, so a row means the same thing
 * however it entered the table.
 */
val CURRENCY_SEED: List<String> = listOf("BRL", "USD", "EUR", "GBP", "CHF", "CNY")

/**
 * The seeding, as something `core/database` can ask for.
 *
 * That module may name neither a locale nor the platform, so the two things only this
 * layer can answer are resolved here and arrive there resolved — the same move
 * [LegacyRelabel] and [SeededBaseCurrency] already make.
 */
interface CurrencySeeding {

    /**
     * The rows the app itself brings: [CURRENCY_SEED] plus the currency the device's
     * locale names, when it has one and it has two decimal places — each already
     * carrying the glyph [symbolOf] resolves for it.
     */
    fun rows(): List<SeedCurrency>

    /**
     * The glyph the platform suggests for a code, in the reader's locale, falling back
     * to the code itself — the same worst case the display already has.
     */
    fun symbolOf(code: String): String
}

/** [CurrencySeeding] answered by the platform and the device's locale. */
class PlatformCurrencySeeding : CurrencySeeding {

    override fun rows(): List<SeedCurrency> {
        // A locale currency of zero or three decimal places is *not* seeded: the app's
        // arithmetic assumes base 100, and in that case the base currency falls back to
        // the last resort, exactly as it does today.
        val locale = localeCurrencyCode()
            ?.uppercase()
            ?.takeIf { it.isNotBlank() && isTwoDecimalCurrency(it) }

        return (CURRENCY_SEED + listOfNotNull(locale))
            .distinct()
            .map { SeedCurrency(it, symbolOf(it)) }
    }

    override fun symbolOf(code: String): String =
        platformCurrency(code)?.symbol?.takeIf { it.isNotBlank() } ?: code
}
