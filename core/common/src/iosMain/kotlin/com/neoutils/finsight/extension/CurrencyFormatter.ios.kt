package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.NSNumberFormatterDecimalStyle
import platform.Foundation.currentLocale
import platform.Foundation.localeIdentifier
import kotlin.math.absoluteValue

actual class CurrencyFormatter internal actual constructor(
    private val symbolOf: (String) -> String,
) {

    /**
     * One formatter per currency, **per glyph** and **per stated precision**, built once
     * and never mutated afterwards. Reconfiguring a shared one before each call is the
     * interleaving failure D10 exists to remove, and the glyph is in the key for the same
     * reason: an edited symbol builds a new formatter rather than mutating a live one.
     */
    private val byCurrency = mutableMapOf<String, NSNumberFormatter>()

    private val plain = mutableMapOf<String, NSNumberFormatter>()

    /**
     * The locale in force **now**, and part of every cache key below.
     *
     * A cached formatter carries the locale it was built under — that is the whole point
     * of caching one — so the key has to say which locale that was. Without it, changing
     * the device language leaves every value wearing the old language's separators until
     * the process dies, which is the same class of failure as a glyph that does not
     * follow the table: the owner of a rule quietly ceasing to own it.
     */
    private val locale: String get() = NSLocale.currentLocale.localeIdentifier

    actual val decimalSeparator: Char
        get() = NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterDecimalStyle
            locale = NSLocale.currentLocale
        }.decimalSeparator?.firstOrNull() ?: '.'

    /**
     * The locale's currency style with **this app's glyph in it**.
     *
     * The currency code is still set, because it is what the locale keys its pattern on;
     * the symbol that comes with it is then replaced, in that order, since setting the
     * code resets the symbol. A code Foundation does not know keeps the pattern all the
     * same, which is why an invented currency renders like every other one.
     */
    private fun formatterOf(
        currency: String,
        symbol: String,
        minFractionDigits: Int?,
        maxFractionDigits: Int?,
    ) = byCurrency.getOrPut("$locale:$currency:$symbol:$minFractionDigits:$maxFractionDigits") {
        NSNumberFormatter().apply {
            numberStyle = NSNumberFormatterCurrencyStyle
            locale = NSLocale.currentLocale
            // Order matters: setting the code resets both the fraction digits and the
            // symbol to that currency's own, so a stated precision and this app's glyph
            // have to come after it.
            currencyCode = currency
            setMinimumFractionDigits((minFractionDigits ?: CENTS).toULong())
            setMaximumFractionDigits((maxFractionDigits ?: CENTS).toULong())
            currencySymbol = symbol
        }
    }

    actual fun format(amount: Double, currency: String) =
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
        // The glyph is the table's answer, whatever Foundation would have said about this
        // code and whatever locale the device is read in.
        val symbol = symbolOf(currency)

        return formatterOf(currency, symbol, minFractionDigits, maxFractionDigits)
            .stringFromNumber(NSNumber(double = amount)) ?: ""
    }

    actual fun formatDecimal(amount: Double, maxFractionDigits: Int): String =
        plain.getOrPut("$locale:$maxFractionDigits") {
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
