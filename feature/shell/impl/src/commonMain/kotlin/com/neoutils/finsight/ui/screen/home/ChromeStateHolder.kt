package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neoutils.finsight.feature.shell.api.ChromeConfig
import com.neoutils.finsight.feature.shell.api.ChromeController

internal class ChromeStateHolder : ChromeController {
    var config by mutableStateOf(ChromeConfig.Default)
        private set

    override fun update(config: ChromeConfig) {
        this.config = config
    }

    override fun reset() {
        config = ChromeConfig.Default
    }
}

@Composable
internal fun rememberChromeStateHolder(): ChromeStateHolder {
    return remember { ChromeStateHolder() }
}
