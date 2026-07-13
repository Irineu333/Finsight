package com.neoutils.finsight.ui.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * True when the window is at least the Medium width breakpoint (≥600dp) — the point where the shell
 * shows a persistent navigation rail instead of a bottom bar. A feature's main screen uses this to
 * drop its back button in wide windows (the rail is the navigation); pushed sub-destinations keep it.
 */
@Composable
fun isWideWindow(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
