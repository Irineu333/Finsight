package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

/**
 * The same read `CurrencyFormatter.ios.kt` already performs: `NSLocale.currentLocale`
 * carries the currency of the device's **region**, which is what decides here — the
 * language does not.
 */
actual fun localeCurrencyCode(): String? = NSLocale.currentLocale.currencyCode
