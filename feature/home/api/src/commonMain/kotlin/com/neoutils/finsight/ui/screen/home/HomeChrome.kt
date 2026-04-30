package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

data class HomeChromeConfig(
    val isBottomBarVisible: Boolean = true,
    val isFloatingActionButtonVisible: Boolean = true,
) {
    companion object {
        val Default = HomeChromeConfig()
        val ContentOnly = HomeChromeConfig(
            isBottomBarVisible = false,
            isFloatingActionButtonVisible = false,
        )
    }
}

interface HomeChromeController {
    fun update(config: HomeChromeConfig)
    fun reset()
}

private object NoOpHomeChromeController : HomeChromeController {
    override fun update(config: HomeChromeConfig) = Unit
    override fun reset() = Unit
}

val LocalHomeChromeController = staticCompositionLocalOf<HomeChromeController> {
    NoOpHomeChromeController
}

@Composable
fun HomeChromeEffect(config: HomeChromeConfig) {
    val controller = LocalHomeChromeController.current
    val currentConfig by rememberUpdatedState(config)

    SideEffect {
        controller.update(currentConfig)
    }

    DisposableEffect(controller) {
        onDispose {
            controller.reset()
        }
    }
}
