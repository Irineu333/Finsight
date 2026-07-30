package com.neoutils.finsight.extension

import java.text.NumberFormat
import java.util.Currency
import kotlin.math.absoluteValue

actual class CurrencyFormatter actual constructor() {

    /**
     * One `NumberFormat` per currency, all of them on the device's locale: the locale
     * decides the *format*, the currency decides the symbol and the denomination.
     */
    private val formats = mutableMapOf<String, NumberFormat>()

    private fun formatOf(currency: String) = formats.getOrPut(currency) {
        NumberFormat.getCurrencyInstance().apply {
            this.currency = Currency.getInstance(currency)
        }
    }

    actual fun format(amount: Double, currency: String): String =
        formatOf(currency).format(amount)

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = formatOf(currency).format(amount.absoluteValue)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
