package com.neoutils.finsight.util

import kotlin.math.abs
import kotlin.math.roundToLong

/** How many decimal places a rate is **shown** with. It is never how one is stored. */
const val RATE_DISPLAY_SCALE: Int = 4

/**
 * Renders a rate with [RATE_DISPLAY_SCALE] decimal places.
 *
 * The stored rate is the full quotient (design D11); this is the other number, with the
 * other owner. Rounding here is a formatting decision and reaches nothing but the
 * screen — no conversion ever reads what this produced, which is what keeps a display
 * from becoming a compounding loss of precision.
 *
 * @param separator the decimal separator, which the caller takes from the string
 * resources so it follows the same language as the text around it.
 */
fun formatRate(rate: Double, separator: String): String {
    var scale = 1L
    repeat(RATE_DISPLAY_SCALE) { scale *= 10 }

    val scaled = (abs(rate) * scale).roundToLong()
    val whole = scaled / scale
    val fraction = (scaled % scale).toString().padStart(RATE_DISPLAY_SCALE, '0')
    val sign = if (rate < 0) "-" else ""

    return "$sign$whole$separator$fraction"
}

/**
 * Reads back what a user typed into a rate field.
 *
 * Both separators are accepted because the keyboard a device offers does not have to
 * agree with the app's language, and a rate refused for a comma is a refusal the user
 * cannot act on. `null` means "not a rate", which is what disables the submit button.
 */
fun String.toRateOrNull(): Double? {
    val normalized = trim().replace(',', '.')
    if (normalized.isEmpty()) return null
    val value = normalized.toDoubleOrNull() ?: return null
    return value.takeIf { it > 0.0 && it.isFinite() }
}
