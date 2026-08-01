package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor() {

    /**
     * One formatter per currency **and per stated precision**, built once and never
     * mutated afterwards. Reconfiguring a shared one before each call is the interleaving
     * failure D10 exists to remove.
     */
    private val byCurrency = mutableMapOf<String, NSNumberFormatter>()

    private val plain = mutableMapOf<Int, NSNumberFormatter>()

    actual val decimalSeparator: Char =
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = NSLocale.currentLocale
        }.decimalSeparator?.firstOrNull() ?: '.'

    private fun formatterOf(
        currency: String,
        minFractionDigits: Int?,
        maxFractionDigits: Int?,
    ) = byCurrency.getOrPut("$currency:$minFractionDigits:$maxFractionDigits") {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale.currentLocale
            // Order matters: setting the currency resets the fraction digits to that
            // currency's own, so a stated precision has to come after it.
            currencyCode = currency
            minFractionDigits?.let { setMinimumFractionDigits(it.toULong()) }
            maxFractionDigits?.let { setMaximumFractionDigits(it.toULong()) }
        }
    }

    actual fun format(amount: Double, currency: String) =
        formatterOf(currency, null, null).stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun format(
        amount: Double,
        currency: String,
        minFractionDigits: Int,
        maxFractionDigits: Int,
    ): String = formatterOf(currency, minFractionDigits, maxFractionDigits)
        .stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun formatDecimal(amount: Double, maxFractionDigits: Int): String =
        plain.getOrPut(maxFractionDigits) {
            NSNumberFormatter().apply {
                numberStyle = NSNumberFormatterDecimalStyle
                locale = NSLocale.currentLocale
                usesGroupingSeparator = false
                setMinimumFractionDigits(0uL)
                setMaximumFractionDigits(maxFractionDigits.toULong())
            }
        }.stringFromNumber(NSNumber(double = amount)) ?: ""

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = format(amount.absoluteValue, currency)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
