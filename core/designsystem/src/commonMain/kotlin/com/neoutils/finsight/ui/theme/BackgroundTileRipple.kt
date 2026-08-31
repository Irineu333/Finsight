package com.neoutils.finsight.ui.theme

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance

/**
 * What pressing or hovering a custom tile looks like, where the tile's rest colour is
 * `colorScheme.background` — a step below the sheet or screen it sits on, and in the dark
 * scheme the darkest ground this theme has.
 *
 * Material's ripple paints the state layer in the surface's content colour at its own
 * alphas (8% hovered, 10% pressed), and a `Surface` coloured `background` defaults that
 * content colour to `onBackground`. In the dark scheme `onBackground` is white, and white at
 * 10% over `background` (`#0F172A`) composites to `#272E3F` — a small arithmetic lift, but a
 * jump of about 11 points of CIE lightness (L*: 8 -> 19), because the sRGB curve that turns a
 * stored colour into light is steepest near black. The same alphas in the light scheme move
 * about 8 L* and *darken* the ground, which reads as a press; painted white on black, the
 * larger, opposite-direction jump reads as the tile becoming a different, lighter surface.
 *
 * So only the dark scheme is touched here, and it is picked out by the ground's own
 * luminance rather than by the platform's day/night flag — the arithmetic above is about the
 * ground, not about which theme produced it. In the dark scheme the layer is painted in
 * `onSurfaceVariant` — the muted tone this theme already spends on secondary text — instead
 * of `onBackground`, at Material's own alphas, left untouched: about +5 L* hovered and +7 L*
 * pressed, gentler than the light scheme's own swing rather than louder than it. The light
 * scheme keeps Material's default untouched.
 */
@Composable
fun BackgroundTileRipple(content: @Composable () -> Unit) {
    val configuration = if (colorScheme.background.luminance() < 0.5f) {
        RippleConfiguration(color = colorScheme.onSurfaceVariant)
    } else {
        RippleConfiguration()
    }

    CompositionLocalProvider(
        LocalRippleConfiguration provides configuration,
        content = content,
    )
}
