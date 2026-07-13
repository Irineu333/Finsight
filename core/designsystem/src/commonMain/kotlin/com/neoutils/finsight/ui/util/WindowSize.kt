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

/**
 * True when the window is at least the Expanded width breakpoint (≥840dp) — the point where the shell
 * reserves a persistent detail pane on the right instead of showing `view*` details as a bottom sheet.
 * Higher than [isWideWindow] on purpose: between Medium and Expanded the rail is already shown, but a
 * pane plus centered content would be too cramped, so details stay a sheet there.
 */
@Composable
fun isExtraWideWindow(): Boolean =
    currentWindowAdaptiveInfo().windowSizeClass
        .isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND)
