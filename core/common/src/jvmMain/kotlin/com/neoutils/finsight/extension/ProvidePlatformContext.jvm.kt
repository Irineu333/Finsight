package com.neoutils.finsight.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
actual fun ProvidePlatformContext(content: @Composable () -> Unit) {
    val windowScope = LocalWindowScope.current
    val platformContext = remember(windowScope) { PlatformContext(windowScope) }
    CompositionLocalProvider(LocalPlatformContext provides platformContext, content = content)
}
