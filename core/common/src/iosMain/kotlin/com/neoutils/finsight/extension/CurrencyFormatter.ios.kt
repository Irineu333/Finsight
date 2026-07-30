package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor() {

    /**
     * One formatter per currency, built once and never mutated afterwards. Reconfiguring
     * a shared one before each call is the interleaving failure D10 exists to remove.
     */
    private val byCurrency = mutableMapOf<String, NSNumberFormatter>()

    private fun formatterOf(currency: String) = byCurrency.getOrPut(currency) {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale.currentLocale
            currencyCode = currency
        }
    }

    actual fun format(amount: Double, currency: String) =
        formatterOf(currency).stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = format(amount.absoluteValue, currency)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
