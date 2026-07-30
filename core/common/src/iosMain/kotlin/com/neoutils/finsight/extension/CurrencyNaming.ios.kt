package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCurrencyCode

actual fun currencyDisplayName(currency: String): String =
    NSLocale.currentLocale.localizedStringForCurrencyCode(currency) ?: currency

/**
 * Asked of a formatter told which currency to render, not of the locale — the locale knows
 * only its **own** currency's symbol, and the picker shows every offered one.
 */
actual fun currencySymbol(currency: String): String = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterCurrencyStyle
    locale = NSLocale.currentLocale
    currencyCode = currency
}.currencySymbol ?: currency
