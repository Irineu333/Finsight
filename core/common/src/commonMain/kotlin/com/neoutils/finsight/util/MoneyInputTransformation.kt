package com.neoutils.finsight.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.runtime.Composable
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

    private fun formatMoney(cents: Long): String {
        val isNegative = cents < 0
        val formatted = formatter.format(abs(cents).toDouble() / 100, currency)
        return if (isNegative) "-$formatted" else formatted
    }
}

/**
 * @param currency the currency of the account the value is being typed for. It is keyed
 * into [remember] so that a field already filled changes symbol when the account changes.
 */
@Composable
fun rememberMoneyInputTransformation(currency: String): MoneyInputTransformation {
    val formatter = LocalCurrencyFormatter.current
    return remember(formatter, currency) { MoneyInputTransformation(formatter, currency) }
}
