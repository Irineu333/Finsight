package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale

actual fun deviceCurrencyCode(): String? = NSNumberFormatter().apply {
    numberStyle = NSNumberFormatterCurrencyStyle
    locale = NSLocale.currentLocale
}.currencyCode
