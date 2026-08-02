package com.neoutils.finsight.extension

import java.util.Currency
import java.util.Locale

/**
 * The same read as the JVM's: `java.util.Currency` names the code in the default locale,
 * suggests the glyph and declares the decimal places, and an unrecognised code becomes
 * the absence this port promises instead of an exception.
 */
actual fun platformCurrency(code: String): PlatformCurrency? =
    runCatching { Currency.getInstance(code.uppercase()) }.getOrNull()?.let {
        PlatformCurrency(
            code = it.currencyCode,
            name = it.getDisplayName(Locale.getDefault()),
            symbol = it.getSymbol(Locale.getDefault()),
            fractionDigits = it.defaultFractionDigits,
        )
    }
