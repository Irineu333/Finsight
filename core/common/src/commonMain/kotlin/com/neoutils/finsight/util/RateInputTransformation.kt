package com.neoutils.finsight.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

/**
 * Keeps a rate field readable as it is typed, **without typing it for the user**.
 *
 * It is deliberately *not* [MoneyInputTransformation]. Money fills from the right because
 * the digits are cents and the last two always are: typing `5`, `0`, `0` means five reais.
 * A rate is not counted that way — `5,32` is five and thirty-two hundredths, typed left to
 * right — and filling from the right turned that into `0,0532`, which is the field typing
 * something the user did not.
 *
 * So the rule keeps what was typed and only refuses what a rate cannot be:
 *
 * - digits and **one** separator survive; every other character is dropped;
 * - the separator the keyboard emitted becomes the language's, so a comma keyboard and a
 *   dot keyboard write the same rate. This is what keeps `5,32` from becoming `532` — the
 *   defect that had the dollar registered at five hundred;
 * - at most [RATE_DISPLAY_SCALE] decimals, which is what the rates screen shows anyway;
 * - a leading separator gets its `0`, because `,5` is a rate the user meant and not one
 *   the app should refuse.
 *
 * What it does **not** do is complete the decimals. `5,5` stays `5,5` while it is being
 * typed; it becomes `5,5000` when it is shown, through [formatRate], which is the one
 * place that decides how a rate reads.
 *
 * @param separator the decimal separator, taken by the caller from the string resources
 * so the field follows the language of the text around it.
 */
class RateInputTransformation(
    private val separator: String,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val kept = keep(asCharSequence().toString())

        if (kept.isEmpty()) {
            delete(0, length)
            return
        }

        if (kept != asCharSequence().toString()) {
            replace(0, length, kept)
            placeCursorAtEnd()
        }
    }

    /**
     * What survives of [text] — public because it *is* the rule, and a rule that can only
     * be observed by driving a text field is a rule no test can state.
     */
    fun keep(text: String): String {
        val builder = StringBuilder()
        var decimals = -1

        for (char in text) {
            when {
                char.isDigit() && decimals < 0 -> builder.append(char)

                char.isDigit() && decimals < RATE_DISPLAY_SCALE -> {
                    builder.append(char)
                    decimals++
                }

                // Past the scale the screen shows: dropped rather than rounded, because
                // rounding here would silently change the number being typed.
                char.isDigit() -> Unit

                // The first separator, whichever the keyboard emitted, becomes the
                // language's. Any after it is not a separator, it is a slip.
                (char == ',' || char == '.') && decimals < 0 -> {
                    if (builder.isEmpty()) builder.append('0')
                    builder.append(separator)
                    decimals = 0
                }

                else -> Unit
            }
        }

        return builder.toString()
    }
}
