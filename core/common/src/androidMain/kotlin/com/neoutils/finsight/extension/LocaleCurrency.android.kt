package com.neoutils.finsight.extension

import java.text.NumberFormat

/**
 * The same read `CurrencyFormatter.android.kt` already performs, used to decide rather
 * than to format: `NumberFormat.getCurrencyInstance()` resolves its currency from the
 * default locale's **country**, so the region decides and the language does not.
 */
actual fun localeCurrencyCode(): String? =
    runCatching { NumberFormat.getCurrencyInstance().currency?.currencyCode }.getOrNull()
