package com.neoutils.finance.ui.icons

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.painter.Painter

fun interface LazyIcon {
    @Composable
    operator fun invoke() : Painter
}