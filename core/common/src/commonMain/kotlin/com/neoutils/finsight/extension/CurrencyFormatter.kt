package com.neoutils.finsight.extension

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Renders money. **The currency comes in through the method, the glyph comes from the
 * table, and the locale governs only the *format*** — separators, grouping and where the
 * symbol sits, which is what a locale legitimately knows (design D10).
 *
 * Deriving the *currency* from the device is what made a phone in `en-US` render `$` over
 * an amount in reais, and there is no overload that formats without saying in what.
 * Deriving the *glyph* from the device was the same failure one step in: the platform
 * answers a symbol for the **device's locale**, so `USD` renders `US$` on a phone in
 * `pt-BR` and `$` on one in `en-US`, and neither is the symbol the user stored. The
 * currency registry says the stored symbol is what appears over a value; this is where
 * that becomes true, because this is the only thing that puts a symbol over a value.
 *
 * **So [symbolOf] is a constructor argument while the currency is a parameter.** The
 * glyph of a code is a property of the *table*, which is one table for the whole app and
 * changes only when the user edits it; the currency of a figure changes term by term
 * inside a single rendering (design D22). What is per-instance and what is per-call
 * follows from that, and not from taste.
 *
 * **The constructor is `internal`**, so the only sites in the whole app that may build
 * one are this module's Koin module and [currencyFormatterOf]. Every default expression
 * that used to fabricate one — the composition local's default,
 * `MoneyInputTransformation`'s constructor default — was a door back to the device
 * locale, and none of them can be reopened from outside.
 *
 * Platform instances are **immutable per currency and glyph, and cached**. The glyph is
 * part of the cache key rather than something set on a live formatter: an edited symbol
 * builds a new one and the old entry simply ages out, so no formatter is ever mutated
 * while another thread is formatting through it. Setting `.currency` on a single shared
 * formatter before each call is the interleaving failure D10 exists to remove —
 * `CreditCardFormViewModel` and `BudgetFormViewModel` format off the main thread, and one
 * of them would print the other's symbol.
 */
expect class CurrencyFormatter internal constructor(symbolOf: (String) -> String) {
    /**
     * The decimal separator of the device locale.
     *
     * It is here because the formatter is the one that always knew the locale. A field
     * that has to accept a typed separator — the exchange rate's — would otherwise need
     * a string resource of its own to know which character the language uses, and a
     * second owner for "what a decimal point looks like" is exactly the divergence that
     * put two separators on the same card once already.
     */
    val decimalSeparator: Char

    fun format(amount: Double, currency: String): String

    /**
     * The same, with the number of decimal places **stated** rather than taken from the
     * currency's own two.
     *
     * It exists for one figure: the exchange rate, which is money in the base currency
     * (so many of it per one unit of another) but not an amount of money *someone paid*.
     * At two places a rate of `0,000691` reads `R$ 0,00` — a rate of zero, which is not
     * a rounding of the truth but a different statement. Asking for a wider maximum lets
     * the common case still read `R$ 5,50`, because a maximum only allows digits; it is
     * the minimum that pads them.
     */
    fun format(
        amount: Double,
        currency: String,
        minFractionDigits: Int,
        maxFractionDigits: Int,
    ): String

    /**
     * A plain decimal in the device locale — no symbol, and **no grouping**.
     *
     * Grouping is off deliberately: this renders the text of an editable field, and a
     * thousands separator there is indistinguishable from a decimal one when the text is
     * read back. What the user typed has to survive the round trip unambiguously.
     */
    fun formatDecimal(amount: Double, maxFractionDigits: Int): String

    fun formatWithSign(amount: Double, currency: String): String
}

/**
 * The decimal places a money figure takes when nobody states otherwise.
 *
 * It is the app's base-100 premise, and not the platform's answer for a code: the offered
 * set admits only two-decimal currencies — `isTwoDecimalCurrency` is what bars the others
 * at registration — so asking the platform again here would only let a code the app never
 * offered decide how its own figures round.
 */
internal const val CENTS = 2

/**
 * A formatter over a **snapshot** of the symbol table, for the composition root.
 *
 * `FormattingLocalsHost` collects the table into composition state and derives one of
 * these from it. That is what makes an edited symbol reach a value already on screen: the
 * instance changes identity when the table does, so everything reading
 * [LocalCurrencyFormatter] recomposes. The `single` in `commonModule` reads the same
 * table live instead, which is what the two form view models need — they format on
 * demand, outside composition, where there is nothing to recompose.
 */
fun currencyFormatterOf(symbols: Map<String, String>) =
    CurrencyFormatter(symbolOf = symbols::symbolOf)

/**
 * No default, on purpose: a default would have to build a formatter, and there is no
 * formatter to build that is not the app's own. A surface that reads this outside
 * `FormattingLocalsHost` is a bug, and it says so instead of silently formatting.
 */
val LocalCurrencyFormatter = staticCompositionLocalOf<CurrencyFormatter> {
    error("No CurrencyFormatter provided")
}
