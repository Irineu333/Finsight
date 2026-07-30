package com.neoutils.finsight.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

/**
 * Formats a rate as it is typed, exactly the way [MoneyInputTransformation] formats
 * money: the digits fill from the right, and the field always reads as a number rather
 * than as whatever the keyboard produced.
 *
 * The scale is [RATE_DISPLAY_SCALE], so `55000` reads `5,5000` — the same four places
 * the rates screen shows, resolved through the same [formatRate] rule, so a rate never
 * reads one way where it is typed and another where it is listed.
 *
 * It also settles the decimal separator for good: a keyboard that emits `.` and a
 * language that writes `,` no longer disagree, because no separator the user types
 * survives — only digits do. That is what keeps `5,32` from silently becoming `532`.
 *
 * @param separator the decimal separator, taken by the caller from the string resources
 * so the field follows the language of the text around it.
 */
class RateInputTransformation(
    private val separator: String,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val formatted = format(asCharSequence().toString())

        if (formatted.isEmpty()) {
            delete(0, length)
            return
        }

        replace(0, length, formatted)
        placeCursorAtEnd()
    }

    /**
     * What the field reads as after [text] was typed into it — public because it *is*
     * the rule, and a rule that can only be observed by driving a text field is a rule
     * no test can state. Empty when there is no digit to read.
     *
     * Formatted from the digits themselves, never through a `Double`: the field is text
     * on its way to becoming a number, and rounding it twice on the way in is how a
     * typed rate stops being what was typed.
     */
    fun format(text: String): String {
        val digits = text.filter { it.isDigit() }.take(MAX_DIGITS)
        if (digits.isEmpty()) return ""

        val padded = digits.padStart(RATE_DISPLAY_SCALE + 1, '0')
        val whole = padded.dropLast(RATE_DISPLAY_SCALE).trimStart('0').ifEmpty { "0" }
        val fraction = padded.takeLast(RATE_DISPLAY_SCALE)
        return "$whole$separator$fraction"
    }

    private companion object {
        /** Enough for any rate the catalog can hold, and short of anything that overflows. */
        const val MAX_DIGITS = 12
    }
}
