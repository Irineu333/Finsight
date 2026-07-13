package com.neoutils.finsight.feature.shell.api

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.staticCompositionLocalOf

data class ChromeConfig(
    val isBottomBarVisible: Boolean = true,
    val isFloatingActionButtonVisible: Boolean = true,
) {
    companion object {
        val Default = ChromeConfig()
        val ContentOnly = ChromeConfig(
            isBottomBarVisible = false,
            isFloatingActionButtonVisible = false,
        )
    }
}

interface ChromeController {
    fun update(config: ChromeConfig)
    fun reset()
}

private object NoOpChromeController : ChromeController {
    override fun update(config: ChromeConfig) = Unit
    override fun reset() = Unit
}

val LocalChromeController = staticCompositionLocalOf<ChromeController> {
    NoOpChromeController
}

@Composable
fun ChromeEffect(config: ChromeConfig) {
    val controller = LocalChromeController.current
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
