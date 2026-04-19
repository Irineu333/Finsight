package com.neoutils.finsight.extension

import java.text.NumberFormat
import kotlin.math.absoluteValue

actual class CurrencyFormatter actual constructor() {
    private val format = NumberFormat.getCurrencyInstance()

    actual fun format(amount: Double) = format.format(amount)

    actual fun formatWithSign(amount: Double): String {
        val formatted = format.format(amount.absoluteValue)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
