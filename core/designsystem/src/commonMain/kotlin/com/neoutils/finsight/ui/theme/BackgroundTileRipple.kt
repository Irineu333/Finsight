package com.neoutils.finsight.ui.theme

import androidx.compose.material3.LocalRippleConfiguration
import androidx.compose.material3.MaterialTheme.colorScheme
import androidx.compose.material3.RippleConfiguration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.luminance

/**
 * What pressing or hovering a custom tile looks like, where the tile's rest colour is a step
 * below the sheet or screen it sits on — `colorScheme.background` itself, or
 * `colorScheme.surfaceContainer`, the next step up from it.
 *
 * Material's ripple paints the state layer in the surface's content colour at its own
 * alphas (8% hovered, 10% pressed), and a `Surface` coloured `background` or
 * `surfaceContainer` defaults that content colour to white in the dark scheme (`onBackground`
 * and `onSurface` are both white there). White at 10% over `background` (`#0F172A`, L* 8)
 * composites to `#272E3F` — a small arithmetic lift, but a jump of about 11 points of CIE
 * lightness, because the sRGB curve that turns a stored colour into light is steepest near
 * black; over the lighter `surfaceContainer` (`#1E293B`, L* 16) the same white at 10% still
 * lifts about 10 L*, because that ground is still dark enough to sit on the curve's steep
 * part. The same alphas in the light scheme move about 6-8 L* and *darken* the ground, which
 * reads as a press; painted white on a dark ground, the larger, opposite-direction jump reads
 * as the tile becoming a different, lighter surface.
 *
 * So only the dark scheme is touched here, and which scheme is in force is read off
 * `colorScheme.background`'s own luminance rather than off the platform's day/night flag or
 * the calling tile's own ground colour — this theme always defines `background` low (the
 * darkest ground the dark scheme has) in the dark scheme and high in the light one, so its
 * luminance crosses the branch's threshold exactly where the scheme does, whichever ground
 * the wrapped tile itself paints. In the dark scheme the layer is painted in
 * `onSurfaceVariant` — the muted tone this theme already spends on secondary text — instead
 * of the surface's default white, at Material's own alphas, left untouched: about +5 L*
 * hovered and +7 L* pressed over `background`, and about +5 L* hovered and +6 L* pressed over
 * `surfaceContainer` — both gentler than the light scheme's own swing over the equivalent
 * ground rather than louder than it. The light scheme keeps Material's default untouched.
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
