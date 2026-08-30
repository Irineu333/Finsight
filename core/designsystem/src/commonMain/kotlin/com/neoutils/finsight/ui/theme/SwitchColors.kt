package com.neoutils.finsight.ui.theme

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable

/**
 * What a switch of this app looks like.
 *
 * Material's own default paints the unchecked thumb with `outline` over a track of
 * `surfaceContainerHighest`, and in this app's dark scheme those two tokens are the same
 * value (`Color.kt`: `DividerColor` and `Surface3` are both `0xFF334155`). The thumb is
 * opaque and still invisible — it reads as a switch with no thumb at all. In the light
 * scheme the pair differs, but only by the distance between two neighbouring greys.
 *
 * So the unchecked thumb is stated here rather than inherited, and `onSurfaceVariant` is
 * what states it: the token already carries "legible over a surface" in both schemes,
 * which is exactly the requirement. Everything else stays Material's default.
 */
@Composable
fun finsightSwitchColors(): SwitchColors = SwitchDefaults.colors(
    uncheckedThumbColor = colorScheme.onSurfaceVariant,
)
