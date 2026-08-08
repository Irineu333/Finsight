package com.neoutils.finsight.extension

import java.util.Currency
import java.util.Locale

/**
 * `java.util.Currency` is the platform's own table: it names the code in the default
 * locale, suggests the glyph, and declares the decimal places. An unrecognised code
 * makes `getInstance` throw, and that is turned into the absence this port promises.
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
