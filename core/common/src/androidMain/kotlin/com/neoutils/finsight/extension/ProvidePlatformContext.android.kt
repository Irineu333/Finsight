package com.neoutils.finsight.extension

import android.app.Activity
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember

@Composable
actual fun ProvidePlatformContext(content: @Composable () -> Unit) {
    val owner = LocalActivityResultRegistryOwner.current
    val platformContext = remember(owner) { PlatformContext(owner as Activity) }
    CompositionLocalProvider(
        LocalPlatformContext provides platformContext,
        content = content
    )
}
