package com.neoutils.finsight.extension

import java.util.Currency
import java.util.Locale

actual fun currencyDisplayName(currency: String): String = runCatching {
    Currency.getInstance(currency).getDisplayName(Locale.getDefault())
}.getOrNull() ?: currency

actual fun currencySymbol(currency: String): String = runCatching {
    Currency.getInstance(currency).getSymbol(Locale.getDefault())
}.getOrNull() ?: currency
