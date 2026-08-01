package com.neoutils.finsight.extension

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Renders money. **The currency comes in through the method; the locale governs only the
 * *format*** — separators and where the symbol sits — which is what a locale legitimately
 * knows (design D10). Deriving the currency from the device is what made a phone in
 * `en-US` render `$` over an amount in reais, and it is the failure this signature
 * closes: there is no overload that formats without saying in what.
 *
 * **Why the currency is a parameter and not a constructor argument.** The unit that gets
 * injected — Koin's `single`, the composition local, the two view models that format
 * outside composition — has to be currency-agnostic anyway, because a multi-term figure
 * (design D22) juxtaposes terms in *different* currencies inside a single rendering. A
 * per-currency formatter would therefore always be reached through a factory, which is
 * this class with one more object in front of it.
 *
 * **The constructor is `internal`**, so the only site in the whole app that may build one
 * is this module's composition root. Every default expression that used to fabricate one
 * — the composition local's default, `MoneyInputTransformation`'s constructor default —
 * was a door back to the device locale, and none of them can be reopened from outside.
 *
 * Platform instances are **immutable per currency and cached**. Setting `.currency` on a
 * single shared formatter before each call would reintroduce the same failure underneath,
 * under interleaving: `CreditCardFormViewModel` and `BudgetFormViewModel` format off the
 * main thread, and one of them would print the other's symbol.
 */
expect class CurrencyFormatter internal constructor() {
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
 * No default, on purpose: a default would have to build a formatter, and there is no
 * formatter to build that is not the app's own. A surface that reads this outside
 * `FormattingLocalsHost` is a bug, and it says so instead of silently formatting.
 */
val LocalCurrencyFormatter = staticCompositionLocalOf<CurrencyFormatter> {
    error("No CurrencyFormatter provided")
}
