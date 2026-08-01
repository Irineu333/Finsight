package com.neoutils.finsight.extension

import java.text.NumberFormat

/**
 * The same read `CurrencyFormatter.android.kt` already performs, used to decide rather
 * than to format: `NumberFormat.getCurrencyInstance()` resolves its currency from the
 * default locale's **country** — which on this platform is the country of the chosen
 * language, and so names a pre-selection rather than a location. Re-denominating an
 * existing database asks the other question, and `DeviceRegion` answers that one.
 */
actual fun localeCurrencyCode(): String? =
    runCatching { NumberFormat.getCurrencyInstance().currency?.currencyCode }.getOrNull()
