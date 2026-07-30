package com.neoutils.finsight.extension

import androidx.compose.runtime.compositionLocalOf

/**
 * Renders an amount as money: the **currency comes from the caller**, and the locale
 * governs only *format* — decimal separator, grouping, and where the symbol sits.
 *
 * The currency enters by parameter rather than by construction, and that is the decision
 * this KDoc records. A single screen renders figures of more than one currency — a list
 * with a BRL account beside a USD one — so an instance per currency would turn the shared
 * `single {}` binding and [LocalCurrencyFormatter] into a factory, without making any
 * call site clearer. Keeping one instance leaves the formatter what it is: the holder of
 * the locale. Caching a platform formatter per currency inside an implementation is a
 * detail, not a change of binding.
 *
 * There is deliberately **no overload that formats without a currency**: deriving it from
 * the locale is how a dollar balance came to render with a `R$` beside it.
 */
expect class CurrencyFormatter() {
    fun format(amount: Double, currency: String): String
    fun formatWithSign(amount: Double, currency: String): String
}

val LocalCurrencyFormatter = compositionLocalOf { CurrencyFormatter() }
