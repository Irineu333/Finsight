package com.neoutils.finsight.extension

import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor() {

    /**
     * One formatter per currency **and per stated precision**, **per thread**. Per key
     * because reconfiguring a shared one before each call is the interleaving failure D10
     * exists to remove; per thread because `NumberFormat.format` mutates internal state
     * even when nothing is reconfigured, and the two form view models format off the main
     * thread.
     */
    private val byCurrency = ConcurrentHashMap<String, ThreadLocal<NumberFormat>>()

    private val plain = ConcurrentHashMap<Int, ThreadLocal<NumberFormat>>()

    private val decimal = ThreadLocal.withInitial {
        NumberFormat.getNumberInstance().apply {
            minimumFractionDigits = 2
            maximumFractionDigits = 2
        }
    }

    actual val decimalSeparator: Char = DecimalFormatSymbols.getInstance().decimalSeparator

    private fun formatterOf(
        currency: String,
        minFractionDigits: Int?,
        maxFractionDigits: Int?,
    ): NumberFormat? {
        val iso = runCatching { Currency.getInstance(currency) }.getOrNull() ?: return null
        return byCurrency.getOrPut("$currency:$minFractionDigits:$maxFractionDigits") {
            ThreadLocal.withInitial {
                NumberFormat.getCurrencyInstance().apply {
                    // Order matters: setting the currency resets the fraction digits to
                    // that currency's own, so a stated precision has to come after it.
                    this.currency = iso
                    minFractionDigits?.let { minimumFractionDigits = it }
                    maxFractionDigits?.let { maximumFractionDigits = it }
                }
            }
        }.get()
    }

    actual fun format(amount: Double, currency: String): String =
        // A code outside ISO 4217 is not the device's currency either: it prints as
        // itself rather than borrowing a symbol that would be a lie.
        formatterOf(currency, null, null)?.format(amount)
            ?: "$currency ${decimal.get().format(amount)}"

    actual fun format(
        amount: Double,
        currency: String,
        minFractionDigits: Int,
        maxFractionDigits: Int,
    ): String =
        formatterOf(currency, minFractionDigits, maxFractionDigits)?.format(amount)
            ?: "$currency ${formatDecimal(amount, maxFractionDigits)}"

    actual fun formatDecimal(amount: Double, maxFractionDigits: Int): String =
        plain.getOrPut(maxFractionDigits) {
            ThreadLocal.withInitial {
                NumberFormat.getNumberInstance().apply {
                    isGroupingUsed = false
                    minimumFractionDigits = 0
                    maximumFractionDigits = maxFractionDigits
                }
            }
        }.get().format(amount)

    actual fun formatWithSign(amount: Double, currency: String): String {
        val formatted = format(amount.absoluteValue, currency)
        return when {
            amount > 0 -> "+$formatted"
            amount < 0 -> "-$formatted"
            else -> formatted
        }
    }
}
