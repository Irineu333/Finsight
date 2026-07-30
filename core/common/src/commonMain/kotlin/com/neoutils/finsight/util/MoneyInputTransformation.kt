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
import com.neoutils.finsight.extension.moneyInput

/**
 * Formats what the user types as money, in [currency].
 *
 * The currency is a constructor parameter and has no default: the field shows the symbol
 * of the account the entry is going to, and typing 100 dollars into a field that says
 * `R$` is the display failure of `money-display`, seen from the writing side.
 */
class MoneyInputTransformation(
    private val currency: String,
    private val formatter: CurrencyFormatter,
) : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val formatted = reformat(asCharSequence().toString())

        if (formatted.isEmpty()) {
            delete(0, length)
            return
        }

        replace(0, length, formatted)

        placeCursorAtEnd()
    }

    /**
     * [text] read as cents and written back in [currency] — the same rule the field applies
     * as the user types, exposed so a field whose currency changed under it can be brought
     * back in line. Empty when there is no digit to read.
     */
    fun reformat(text: String): String {
        val isNegative = text.startsWith("-")

        val digitsOnly = text.filter { it.isDigit() }

        if (digitsOnly.isEmpty()) return ""

        var cents = digitsOnly.toLongOrNull() ?: 0L

        if (isNegative) {
            cents = -cents
        }

        return formatter.moneyInput(cents, currency)
    }
}

/**
 * The transformation for [state], denominated in [currency].
 *
 * [state] is not optional, and that is the point: an [InputTransformation] only runs on
 * *input*, so a field the user already filled keeps whatever symbol it was written with. Pick
 * a dollar account under a filled field and it would go on reading `R$` until the next
 * keystroke — the same wrong-legend failure, arrived at by standing still. Whenever the
 * currency changes, what is already typed is re-read as cents and written back in the new one:
 * the amount the user entered is untouched, only its denomination moves.
 */
@Composable
fun rememberMoneyInputTransformation(
    currency: String,
    state: TextFieldState,
): MoneyInputTransformation {
    val formatter = LocalCurrencyFormatter.current
    val transformation = remember(formatter, currency) {
        MoneyInputTransformation(currency, formatter)
    }

    LaunchedEffect(transformation, state) {
        val typed = state.text.toString()
        if (typed.isNotEmpty()) {
            state.setTextAndPlaceCursorAtEnd(transformation.reformat(typed))
        }
    }

    return transformation
}
