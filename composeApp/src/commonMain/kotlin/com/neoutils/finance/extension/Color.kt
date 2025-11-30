package com.neoutils.finance.extension

import androidx.compose.ui.graphics.Color

fun String.toColor(): Color {
    val hex = this.removePrefix("#")
    val color = hex.toLongOrNull(16) ?: 0xFF000000
    return Color(color)
}
