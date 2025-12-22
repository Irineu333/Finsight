package com.neoutils.finance.util

import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldBuffer
import androidx.compose.foundation.text.input.delete
import androidx.compose.foundation.text.input.placeCursorAtEnd

class DayInputTransformation(private val minDay: Int = 1, private val maxDay: Int = 28) :
        InputTransformation {

    override fun TextFieldBuffer.transformInput() {
        val digitsOnly = asCharSequence().filter { it.isDigit() }

        if (digitsOnly.isEmpty()) {
            delete(0, length)
            return
        }

        val limited = digitsOnly.take(2)
        val value = limited.toString().toIntOrNull() ?: 0

        val clamped =
                when {
                    value < minDay && limited.length == 2 -> minDay.toString()
                    value > maxDay -> maxDay.toString()
                    else -> limited.toString()
                }

        replace(0, length, clamped)
        placeCursorAtEnd()
    }
}
