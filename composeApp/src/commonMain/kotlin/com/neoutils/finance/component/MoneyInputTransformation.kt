package com.neoutils.finance.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class MoneyInputTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {

        val digitsOnly = asCharSequence().filter { it.isDigit() }.toString()

        if (digitsOnly.isEmpty()) {
            delete(0, length)
            return
        }

        val cents = digitsOnly.toLongOrNull() ?: 0L

        val formatted = formatMoney(cents)

        replace(0, length, formatted)

        placeCursorAtEnd()
    }

    private fun formatMoney(cents: Long): String {
        val reais = cents / 100
        val centavos = cents % 100

        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        return "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
    }
}
