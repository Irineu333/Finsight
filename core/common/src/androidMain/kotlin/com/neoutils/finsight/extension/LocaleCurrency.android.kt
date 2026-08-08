package com.neoutils.finsight.extension

import java.text.NumberFormat

/**
 * The same read `CurrencyFormatter.android.kt` already performs, used to decide rather
 * than to format: `NumberFormat.getCurrencyInstance()` resolves its currency from the
 * default locale's **country** — which on this platform is the country of the chosen
 * language, and so names what the user has been *reading* rather than where they are.
 * It is the reading that both callers want, and the legacy relabel most of all: it
 * exists to keep that symbol from changing.
 */
actual fun localeCurrencyCode(): String? =
    runCatching { NumberFormat.getCurrencyInstance().currency?.currencyCode }
        .getOrNull()
        ?.takeIf { it != NO_CURRENCY }
