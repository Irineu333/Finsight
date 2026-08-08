package com.neoutils.finsight.extension

import platform.Foundation.NSLocale
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterCurrencyStyle
import platform.Foundation.currentLocale
import platform.Foundation.localizedStringForCurrencyCode

/**
 * `NSLocale` names the code in the current language, and a currency-styled
 * `NSNumberFormatter` carries the glyph and the decimal places the system attaches to
 * it. Being unable to name the code is how this platform states it does not know it,
 * and that is the absence this port promises.
 */
actual fun platformCurrency(code: String): PlatformCurrency? {
    val normalized = code.uppercase()
    val name = NSLocale.currentLocale.localizedStringForCurrencyCode(normalized) ?: return null
    val formatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterCurrencyStyle
        locale = NSLocale.currentLocale
        currencyCode = normalized
    }
    return PlatformCurrency(
        code = normalized,
        name = name,
        symbol = formatter.currencySymbol ?: normalized,
        fractionDigits = formatter.maximumFractionDigits.toInt(),
    )
}
