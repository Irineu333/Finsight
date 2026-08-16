package com.neoutils.finsight.ui.component

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchColors
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The app's switch, and the one place its colours are decided.
 *
 * Material's own defaults paint the unchecked thumb in `outline`, which against this app's
 * dark surfaces is close enough to the track that the thumb reads as absent — a switch that
 * looks like an empty pill rather than one that is off. Every screen that noticed fixed it
 * where it stood, so the same control had four appearances and one of them was a literal
 * grey that never followed the theme at all.
 *
 * The colours below are that fix, decided once: the checked side states the accent plainly,
 * the unchecked side keeps the thumb readable against its own track, and the disabled side
 * dims without vanishing — an unavailable switch still has to say which way it is set.
 */
@Composable
fun FinsightSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) = Switch(
    checked = checked,
    onCheckedChange = onCheckedChange,
    modifier = modifier,
    enabled = enabled,
    colors = FinsightSwitchDefaults.colors(),
)

/**
 * The colours [FinsightSwitch] is built from, exposed for the rare control that cannot be the
 * component itself. Reach for [FinsightSwitch] first: a switch that takes its own colours is
 * how the four appearances happened.
 */
object FinsightSwitchDefaults {

    @Composable
    fun colors(): SwitchColors = SwitchDefaults.colors(
        checkedThumbColor = colorScheme.primary,
        checkedTrackColor = colorScheme.primary.copy(alpha = CHECKED_TRACK_ALPHA),
        checkedBorderColor = colorScheme.primary,
        uncheckedThumbColor = colorScheme.onSurfaceVariant,
        uncheckedTrackColor = colorScheme.surfaceVariant,
        uncheckedBorderColor = colorScheme.outline,
        disabledCheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        disabledCheckedTrackColor = colorScheme.surfaceVariant,
        disabledCheckedBorderColor = colorScheme.outlineVariant,
        disabledUncheckedThumbColor = colorScheme.onSurfaceVariant.copy(alpha = DISABLED_ALPHA),
        disabledUncheckedTrackColor = colorScheme.surfaceVariant,
        disabledUncheckedBorderColor = colorScheme.outlineVariant,
    )

    /** The checked track is the accent held back, so the thumb on top of it stays the figure. */
    private const val CHECKED_TRACK_ALPHA = 0.35f

    /** Dimmed enough to read as unavailable, and not so much that the thumb disappears. */
    private const val DISABLED_ALPHA = 0.6f
}
