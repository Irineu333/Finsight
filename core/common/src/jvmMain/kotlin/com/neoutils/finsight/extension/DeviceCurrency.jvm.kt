package com.neoutils.finsight.extension

import java.text.NumberFormat

actual fun deviceCurrencyCode(): String? = runCatching {
    NumberFormat.getCurrencyInstance().currency?.currencyCode
}.getOrNull()
