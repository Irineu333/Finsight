package com.neoutils.finsight.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencyFormatter

/**
 * How many decimal places a typed rate may carry.
 *
 * It is not a display decision — the archive stores the **full quotient** and the screens
 * show as many places as they need — it is how far the field lets the user go before it
 * stops accepting digits. Eight covers the whole curated catalog with room to spare: the
 * narrowest pair in it is a rate around `0,0007`, which needs six to be a number at all
 * and not a rounded zero.
 */
const val RATE_SCALE = 8

/**
 * A typed exchange rate, filtered as the user types — **never reformatted**.
 *
 * A rate is money in the base currency (so many of it per one unit of another), and it is
 * read through the same [CurrencyFormatter] as every other amount in the app. But it is
 * not *entered* like one, and conflating the two is what this class exists to undo:
 * `MoneyInputTransformation` fills from the right because the last two digits **are**
 * cents, and a rate has nothing of the sort. `5,32` is five and thirty-two hundredths,
 * typed left to right; under the money rule it became `0,0532`, and — worse — a rate like
 * `0,000691` was not expressible at all, because two decimal places round it to zero.
 * A rate of zero is not a rounding of the truth, it is a different statement, and the
 * archive is supposed to hold the full quotient (`currency-consolidation`).
 *
 * So the rule here only **refuses what a rate cannot be**: anything other than digits, a
 * second separator, and more than [RATE_SCALE] decimal places. It does not complete the
 * places either — `5,5` stays `5,5` while it is being typed and reads `R$ 5,50` once it is
 * displayed, by the formatter, which is the single owner of how a number reads.
 *
 * **The separator the keyboard offers becomes the separator the language uses.** A `.`
 * typed on an English keyboard under a pt-BR locale is written as `,`, so keyboard and
 * language cannot disagree and turn `5.32` into `532`. Which character the language uses
 * comes from [CurrencyFormatter.decimalSeparator] — the formatter always knew the locale,
 * and a second owner for that fact is a divergence waiting to happen.
 */
class RateInputTransformation(
    private val separator: Char,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val typed = asCharSequence().toString()
        val accepted = filterTyped(typed)

        if (accepted == typed) return

        replace(0, length, accepted)
        placeCursorAtEnd()
    }

    /**
     * [text] with everything a rate cannot contain removed — exposed so the same rule can
     * be stated as a test without a text field to drive it.
     */
    fun filterTyped(text: String): String = buildString {
        var separated = false
        var decimals = 0

        for (char in text) {
            when {
                char.isDigit() -> {
                    if (separated) {
                        if (decimals == RATE_SCALE) continue
                        decimals++
                    }
                    append(char)
                }

                !separated && (char == separator || char == '.' || char == ',') -> {
                    separated = true
                    // A separator typed first means "nought point something", which is a
                    // rate below one — the very case two decimal places used to swallow.
                    if (isEmpty()) append('0')
                    append(separator)
                }
            }
        }
    }
}

/** The transformation for a rate field, in the device locale's separator. */
@Composable
fun rememberRateInputTransformation(): RateInputTransformation {
    val formatter = LocalCurrencyFormatter.current
    return remember(formatter) { RateInputTransformation(formatter.decimalSeparator) }
}

/**
 * What a rate field says, as a number — `null` when it says nothing yet.
 *
 * It reads the same three characters the field accepts, so a half-typed `5,` is simply
 * `5` and never an error to report: an unfinished number is a form not yet filled in, and
 * the submit button just does not enable.
 */
fun String.rateToDoubleOrNull(separator: Char): Double? {
    val normalized = buildString {
        var separated = false
        for (char in this@rateToDoubleOrNull) {
            when {
                char.isDigit() -> append(char)
                !separated && (char == separator || char == '.' || char == ',') -> {
                    separated = true
                    append('.')
                }
            }
        }
    }
    return normalized.toDoubleOrNull()
}

/**
 * A stored rate as the text of an editable field: the **full** value, up to [RATE_SCALE]
 * places, with no symbol and no grouping.
 *
 * This is the round trip the archive depends on. Seeding the field with the *displayed*
 * form would mean that opening a rate of `5,4321` and saving it unchanged wrote back
 * `5,43` — and, because a corrected rate is the user's and the user's prevails, that
 * rounding would then outrank the observation it came from.
 */
fun CurrencyFormatter.formatRateForEditing(rate: Double): String =
    formatDecimal(rate, RATE_SCALE)
