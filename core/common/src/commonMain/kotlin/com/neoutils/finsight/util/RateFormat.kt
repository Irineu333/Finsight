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
 * It lives here, and not beside the screen that edits rates, because it has **one**
 * owner: the rate a cross-currency form shows as a consequence of the two amounts typed
 * has to read exactly like the same rate on the rates screen, or the user meets
 * `5,3201` in one place and `5,32` in the other for one observation.
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
