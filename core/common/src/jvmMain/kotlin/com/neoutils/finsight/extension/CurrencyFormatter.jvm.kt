package com.neoutils.finsight.extension

import java.text.NumberFormat
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor() {

    /**
     * One formatter per currency **per thread**. Per currency because reconfiguring a
     * shared one before each call is the interleaving failure D10 exists to remove; per
     * thread because `NumberFormat.format` mutates internal state even when nothing is
     * reconfigured, and the two form view models format off the main thread.
     */
    private val byCurrency = ConcurrentHashMap<String, ThreadLocal<NumberFormat>>()

    private val decimal = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    private fun formatterOf(currency: String): NumberFormat? {
        val iso = runCatching { Currency.getInstance(currency) }.getOrNull() ?: return null
        return byCurrency.getOrPut(currency) {
            ThreadLocal.withInitial {
                NumberFormat.getCurrencyInstance().apply { this.currency = iso }
            }
        }.get()
    }

    actual fun format(amount: Double, currency: String): String =
        // A code outside ISO 4217 is not the device's currency either: it prints as
        // itself rather than borrowing a symbol that would be a lie.
        formatterOf(currency)?.format(amount) ?: "$currency ${decimal.get().format(amount)}"

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = format(amount.absoluteValue, currency)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
