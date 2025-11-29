package com.neoutils.finance.component

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class DateInputTransformation : InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val digitsOnly = asCharSequence().filter { it.isDigit() }.toString()

        val limited = digitsOnly.take(8)

        if (limited.isEmpty()) {
            delete(0, length)
            return
        }

        val formatted = formatDate(limited)

        replace(0, length, formatted)

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
