package com.neoutils.finsight.extension

import androidx.compose.runtime.staticCompositionLocalOf

expect class PlatformContext

val LocalPlatformContext = staticCompositionLocalOf<PlatformContext> {
    error("No PlatformContext provided")
}