@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.neoutils.finsight.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * Painting order inside the transition overlay, from bottom to top.
 *
 * While a transition is active the overlay is a second drawing surface: whoever joins it leaves
 * behind the order its containers had established, and is painted by priority instead. This scale
 * is the only place that order is written — every participant states its level from here, the
 * lowest one included, so no position is inherited from the framework default.
 *
 * The levels must stay distinct. Equal priorities are broken by the order the nodes were attached,
 * which for the `Scaffold` slots is the reverse of the normal drawing order — the FAB is
 * subcomposed before the bottom bar but placed after it. Sharing a level therefore flips the
 * chrome's appearance depending on whether a transition happens to be running.
 */
object OverlayPriority {

    /** Shared elements — below the whole shell chrome, whatever their trajectory. */
    const val SharedElement = 0f

    /** The bottom navigation bar and the navigation rail: the same thing at different widths. */
    const val NavigationChrome = 1f

    /**
     * The scrim of the action button's open menu. Above the navigation chrome on purpose: the
     * menu dismisses what is behind it, and a bar left uncovered would go on taking taps.
     */
    const val ActionScrim = 2f

    /** The FAB, docked over the bottom bar — mirrors the `Scaffold` placement order. */
    const val FloatingActionButton = 3f
}

@Composable
context(scope: AnimatedVisibilityScope)
fun AnimatedVisibilityScopeProvider(
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalAnimatedVisibilityScope provides scope,
        content = content,
    )
}

@Composable
fun SharedTransitionProvider(
    content: @Composable SharedTransitionScope.() -> Unit
) {
    SharedTransitionLayout {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this
        ) {
            content()
        }
    }
}
