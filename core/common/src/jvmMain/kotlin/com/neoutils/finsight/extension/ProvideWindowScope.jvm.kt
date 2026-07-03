package com.neoutils.finsight.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.window.WindowScope

internal val LocalWindowScope = staticCompositionLocalOf<WindowScope> {
    error("No WindowScope provided")
}

@Composable
fun WindowScope.ProvideWindowScope(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalWindowScope provides this, content = content)
}
