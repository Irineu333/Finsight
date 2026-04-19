package com.neoutils.finsight.extension

import androidx.compose.runtime.compositionLocalOf

expect class CurrencyFormatter() {
    fun format(amount: Double): String
    fun formatWithSign(amount: Double): String
}

val LocalCurrencyFormatter = compositionLocalOf { CurrencyFormatter() }
