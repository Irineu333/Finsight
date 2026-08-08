package com.neoutils.finsight.extension

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor(
    private val symbolOf: (String) -> String,
) {

    /**
     * One formatter per currency, **per glyph** and **per stated precision**, **per
     * thread**. Per key because reconfiguring a shared one before each call is the
     * interleaving failure D10 exists to remove — and the glyph belongs in the key for
     * that same reason: an edited symbol builds a new formatter instead of mutating one
     * another thread is formatting through. Per thread because `NumberFormat.format`
     * mutates internal state even when nothing is reconfigured, and the two form view
     * models format off the main thread.
     */
    private val byCurrency = ConcurrentHashMap<String, ThreadLocal<DecimalFormat?>>()

    private val plain = ConcurrentHashMap<String, ThreadLocal<NumberFormat>>()

    /**
     * The locale in force **now**, and part of every cache key below.
     *
     * A cached formatter carries the locale it was built under — that is the whole point
     * of caching one — so the key has to say which locale that was. Without it, changing
     * the device language leaves every value wearing the old language's separators until
     * the process dies, which is the same class of failure as a glyph that does not
     * follow the table: the owner of a rule quietly ceasing to own it.
     */
    private val locale: String get() = Locale.getDefault().toLanguageTag()

    actual val decimalSeparator: Char get() = DecimalFormatSymbols.getInstance().decimalSeparator

    /**
     * The locale's currency pattern with **this app's glyph in it**.
     *
     * The ISO currency is still set when the platform knows the code, because that is
     * what a locale keys its pattern on; the symbol it brings with it is then replaced,
     * in that order, since setting the currency resets the symbol. A code the platform
     * does not know keeps the locale's pattern all the same — which is why an invented
     * currency now renders like every other one instead of as a bare code.
     *
     * `null` only where the platform's currency format is not a [DecimalFormat] and so
     * cannot be told which glyph to use.
     */
    private fun formatterOf(
        currency: String,
        symbol: String,
        minFractionDigits: Int?,
        maxFractionDigits: Int?,
    ): DecimalFormat? = byCurrency.getOrPut("$locale:$currency:$symbol:$minFractionDigits:$maxFractionDigits") {
        ThreadLocal.withInitial {
            (NumberFormat.getCurrencyInstance() as? DecimalFormat)?.apply {
                val iso = runCatching { Currency.getInstance(currency) }.getOrNull()

                // Order matters: setting the currency resets both the fraction digits and
                // the symbol to that currency's own, so a stated precision and this app's
                // glyph have to come after it.
                if (iso != null) this.currency = iso

                minimumFractionDigits = minFractionDigits ?: CENTS
                maximumFractionDigits = maxFractionDigits ?: CENTS

                decimalFormatSymbols = decimalFormatSymbols.apply { currencySymbol = symbol }
            }
        }
    }.get()

    actual fun format(amount: Double, currency: String): String =
        formatMoney(amount, currency, minFractionDigits = null, maxFractionDigits = null)

    actual fun format(
        amount: Double,
        currency: String,
        minFractionDigits: Int,
        maxFractionDigits: Int,
    ): String = formatMoney(amount, currency, minFractionDigits, maxFractionDigits)

    private fun formatMoney(
        amount: Double,
        currency: String,
        minFractionDigits: Int?,
        maxFractionDigits: Int?,
    ): String {
        // The glyph is the table's answer, whatever the platform would have said about
        // this code and whatever locale the device is read in.
        val symbol = symbolOf(currency)

        return formatterOf(currency, symbol, minFractionDigits, maxFractionDigits)?.format(amount)
            ?: "$symbol ${formatDecimal(amount, maxFractionDigits ?: CENTS)}"
    }

    actual fun formatDecimal(amount: Double, maxFractionDigits: Int): String =
        plain.getOrPut("$locale:$maxFractionDigits") {
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
