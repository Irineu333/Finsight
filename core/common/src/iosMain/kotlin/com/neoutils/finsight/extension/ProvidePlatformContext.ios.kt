package com.neoutils.finsight.extension

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.uikit.LocalUIViewController

@Composable
actual fun ProvidePlatformContext(content: @Composable () -> Unit) {
    val viewController = LocalUIViewController.current
    val platformContext = remember(viewController) { PlatformContext(viewController) }
    CompositionLocalProvider(LocalPlatformContext provides platformContext, content = content)
}
