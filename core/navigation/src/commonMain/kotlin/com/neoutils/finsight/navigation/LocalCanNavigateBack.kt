package com.neoutils.finsight.navigation

import androidx.compose.runtime.compositionLocalOf

/**
 * Whether the current screen should surface a back affordance. Computed by the navigation shell from
 * the back stack and window size (a section root under a persistent rail has nowhere useful to go
 * back to), and consumed by each screen's top bar. Defaults to `true` so screens rendered outside
 * the shell (e.g. previews) keep their back button.
 */
val LocalCanNavigateBack = compositionLocalOf { true }
