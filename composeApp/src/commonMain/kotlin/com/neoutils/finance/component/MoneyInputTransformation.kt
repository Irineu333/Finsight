package com.neoutils.finance.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class MoneyInputTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val text = asCharSequence().toString()
        
        // Check if the value should be negative
        val isNegative = text.startsWith("-")
        
        // Extract only digits
        val digitsOnly = text.filter { it.isDigit() }.toString()

        if (digitsOnly.isEmpty()) {
            delete(0, length)
            return
        }

        var cents = digitsOnly.toLongOrNull() ?: 0L
        
        // Apply negative sign if needed
        if (isNegative) {
            cents = -cents
        }

        val formatted = formatMoney(cents)

        replace(0, length, formatted)

        placeCursorAtEnd()
    }

    private fun formatMoney(cents: Long): String {
        val isNegative = cents < 0
        val absoluteCents = kotlin.math.abs(cents)
        
        val reais = absoluteCents / 100
        val centavos = absoluteCents % 100

        val reaisFormatted = reais.toString()
            .reversed()
            .chunked(3)
            .joinToString(".")
            .reversed()

        val formatted = "R$ $reaisFormatted,${centavos.toString().padStart(2, '0')}"
        
        return if (isNegative) "-$formatted" else formatted
    }
}
