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
