package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.currencyCode
import platform.Foundation.currentLocale

/**
 * The same read `CurrencyFormatter.ios.kt` already performs: `NSLocale.currentLocale`
 * carries the currency of the device's **region**, which is what decides here — the
 * language does not.
 *
 * This is the platform where the reading and the location happen to coincide, because
 * *Region* is a setting of its own here. Nothing depends on that coincidence: what both
 * callers ask for is the reading, and on iOS the reading is this.
 */
actual fun localeCurrencyCode(): String? =
    NSLocale.currentLocale.currencyCode?.takeIf { it != NO_CURRENCY }
