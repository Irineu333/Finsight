package com.neoutils.finance.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class DateInputTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        // Remove tudo que não é dígito
        val digitsOnly = asCharSequence().filter { it.isDigit() }.toString()

        // Limita a 8 dígitos (DDMMYYYY)
        val limited = digitsOnly.take(8)

        if (limited.isEmpty()) {
            delete(0, length)
            return
        }

        // Formata como DD/MM/YYYY
        val formatted = formatDate(limited)

        // Substitui o conteúdo
        replace(0, length, formatted)

        // Coloca o cursor no final
        placeCursorAtEnd()
    }

    private fun formatDate(digits: String): String {
        return buildString {
            digits.forEachIndexed { index, char ->
                when (index) {
                    2, 4 -> append("/")
                }
                append(char)
            }
        }
    }
}
