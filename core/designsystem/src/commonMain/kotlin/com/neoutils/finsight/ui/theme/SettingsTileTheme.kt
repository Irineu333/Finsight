package com.neoutils.finsight.ui.theme

import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.MaterialTheme.typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.alorma.compose.settings.ui.base.internal.LocalSettingsTextStyles
import com.alorma.compose.settings.ui.base.internal.LocalSettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults

/**
 * What a settings tile of this app looks like, stated once: cards on `surfaceContainer`,
 * with the accent kept for the glyph rather than spent on the title. The tiles read their
 * defaults from these two locals, so a tile added later takes the same look.
 *
 * It lives here rather than beside a screen because it configures theme tokens and knows
 * nothing about settings, and more than one screen is built out of tiles.
 *
 * The tile library is an `implementation` dependency of this module on purpose: what
 * crosses the boundary is this look, and not a second vocabulary of rows for every
 * feature to reach for. A screen that renders tiles depends on the library itself.
 */
@Composable
fun SettingsTileTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalSettingsTileColors provides SettingsTileDefaults.colors(
            containerColor = colorScheme.surfaceContainer,
            titleColor = colorScheme.onSurface,
            subtitleColor = colorScheme.onSurfaceVariant,
            actionColor = colorScheme.onSurfaceVariant,
            groupTitleColor = colorScheme.onSurfaceVariant,
        ),
        LocalSettingsTextStyles provides SettingsTileDefaults.textStyles(
            groupTitleStyle = typography.labelLarge,
            titleStyle = typography.titleMedium,
            subtitleStyle = typography.bodyMedium,
        ),
        content = content,
    )
}
