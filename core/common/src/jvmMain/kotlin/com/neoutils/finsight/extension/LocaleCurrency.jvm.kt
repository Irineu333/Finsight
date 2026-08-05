package com.neoutils.finsight.extension

import java.text.NumberFormat

/**
 * The same read `CurrencyFormatter.jvm.kt` already performs, used to decide rather
 * than to format: `NumberFormat.getCurrencyInstance()` resolves its currency from the
 * default locale's **country** — the operating system's region setting on macOS and
 * Windows, the territory of `LANG` on Linux. Either way it is what put a symbol over
 * every value the desktop app has ever rendered, which is what both callers ask about.
 */
actual fun localeCurrencyCode(): String? =
    runCatching { NumberFormat.getCurrencyInstance().currency?.currencyCode }
        .getOrNull()
        ?.takeIf { it != NO_CURRENCY }
