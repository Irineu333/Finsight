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

class MoneyInputTransformation(
    private val formatter: CurrencyFormatter = CurrencyFormatter()
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
        val formatted = formatter.format(abs(cents).toDouble() / 100)
        return if (isNegative) "-$formatted" else formatted
    }
}

@Composable
fun rememberMoneyInputTransformation(): MoneyInputTransformation {
    val formatter = LocalCurrencyFormatter.current
    return remember(formatter) { MoneyInputTransformation(formatter) }
}
