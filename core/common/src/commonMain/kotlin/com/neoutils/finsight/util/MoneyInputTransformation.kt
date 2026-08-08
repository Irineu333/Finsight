package com.neoutils.finsight.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.LocalCurrencyFormatter
import kotlin.math.abs

/**
 * Formats a money field as the user types, **in the currency of the account chosen** —
 * never the device locale's. Typing 100 into a dollar account with `R$` in the field is
 * the same failure `DisplayAmount` closes on the reading side, from the writing side
 * (design D10).
 *
 * Neither the formatter nor the currency has a default. The formatter's used to, and that
 * default was a door straight back to the device locale.
 */
class MoneyInputTransformation(
    private val formatter: CurrencyFormatter,
    private val currency: String,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()

        val isNegative = text.startsWith("-")

        val digitsOnly = text.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) {
            delete(0, length)
            return
        }

        var cents = digitsOnly.toLongOrNull() ?: 0L

        if (isNegative) {
            cents = -cents
        }

        val formatted = formatMoney(cents)

        replace(0, length, formatted)

        placeCursorAtEnd()
    }

    /**
     * [text] read as cents and written back in [currency] — the same rule the field
     * applies as the user types, exposed so that a field whose currency changed *under*
     * it can be brought back in line. Empty when there is no digit to read.
     */
    fun reformat(text: String): String {
        val digitsOnly = text.filter { it.isDigit() }
        if (digitsOnly.isEmpty()) return ""

        val cents = digitsOnly.toLongOrNull() ?: 0L
        return formatMoney(if (text.startsWith("-")) -cents else cents)
    }

    private fun formatMoney(cents: Long): String {
        val isNegative = cents < 0
        val formatted = formatter.format(abs(cents).toDouble() / 100, currency)
        return if (isNegative) "-$formatted" else formatted
    }
}

/**
 * The transformation for [state], denominated in [currency].
 *
 * **[state] is not optional, and that is the whole point.** An [InputTransformation] runs
 * on *input*, so re-creating it changes nothing about text the user already typed: pick a
 * dollar account under a filled field and it goes on reading `R$ 100,00` until the next
 * keystroke — while the submit debits 100 dollars. It is the same wrong-legend failure
 * design D10 closes on the reading side, reached by standing still.
 *
 * So whenever the currency changes, what is already typed is re-read as cents and written
 * back in the new one. The amount the user entered is untouched; only its denomination
 * moves — which is exactly what changing the account means.
 */
@Composable
fun rememberMoneyInputTransformation(
    currency: String,
    state: TextFieldState,
): MoneyInputTransformation {
    val formatter = LocalCurrencyFormatter.current
    val transformation = remember(formatter, currency) {
        MoneyInputTransformation(formatter, currency)
    }

    LaunchedEffect(transformation, state) {
        val typed = state.text.toString()
        if (typed.isNotEmpty()) {
            state.setTextAndPlaceCursorAtEnd(transformation.reformat(typed))
        }
    }

    return transformation
}
