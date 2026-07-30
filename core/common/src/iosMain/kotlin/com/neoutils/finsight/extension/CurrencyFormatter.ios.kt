package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import kotlin.math.absoluteValue

actual class CurrencyFormatter actual constructor() {

    /**
     * One formatter per currency, all of them on the device's locale: the locale decides
     * the *format*, the currency decides the symbol and the denomination.
     */
    private val formatters = mutableMapOf<String, NSNumberFormatter>()

    private fun formatterOf(currency: String) = formatters.getOrPut(currency) {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale.currentLocale
            currencyCode = currency
        }
    }

    actual fun format(amount: Double, currency: String) =
        formatterOf(currency).stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = formatterOf(currency)
            .stringFromNumber(NSNumber(double = amount.absoluteValue)) ?: ""
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
