package com.neoutils.finsight.ui.util

import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.window.core.layout.WindowSizeClass

/**
 * How much room the window gives, in the three widths the app adapts to. Ordered from narrowest
 * to widest, so a rule that holds from a breakpoint upwards reads as a comparison.
 *
 * - [COMPACT] (<600dp): the shell navigates with a bottom bar and a floating action button.
 * - [WIDE] (≥600dp): the bottom bar gives way to a persistent navigation rail.
 * - [LARGE] (≥840dp): a detail pane is kept on the right, instead of showing details as a sheet.
 */
enum class WindowMode {
    COMPACT,
    WIDE,
    LARGE;

    companion object {
        /** Every mode — what a component that adapts to all of them declares. */
        val ALL: Set<WindowMode> = entries.toSet()
    }
}

/** The mode of the window this composition is in. */
@Composable
fun windowMode(): WindowMode {
    val windowSizeClass = currentWindowAdaptiveInfo().windowSizeClass

    return when {
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_EXPANDED_LOWER_BOUND) -> WindowMode.LARGE
        windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND) -> WindowMode.WIDE
        else -> WindowMode.COMPACT
    }
}

/**
 * True from [WindowMode.WIDE] upwards — the point where the shell shows a persistent navigation
 * rail instead of a bottom bar. A feature's main screen uses this to drop its back button in wide
 * windows (the rail is the navigation); pushed sub-destinations keep it.
 */
@Composable
fun isWideWindow(): Boolean = windowMode() >= WindowMode.WIDE

/**
 * True at [WindowMode.LARGE] — the point where the shell reserves a persistent detail pane on the
 * right instead of showing `view*` details as a bottom sheet. Higher than [isWideWindow] on purpose:
 * at [WindowMode.WIDE] the rail is already shown, but a pane plus centered content would be too
 * cramped, so details stay a sheet there.
 */
@Composable
fun isExtraWideWindow(): Boolean = windowMode() >= WindowMode.LARGE
