package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import kotlin.math.absoluteValue

actual class CurrencyFormatter actual constructor() {
    private val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        locale = NSLocale.currentLocale
    }

    actual fun format(amount: Double) =
        formatter.stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun formatWithSign(amount: Double): String {
        val formatted = formatter.stringFromNumber(NSNumber(double = amount.absoluteValue)) ?: ""
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
