package com.neoutils.finsight.ui.screen.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.neoutils.finsight.feature.home.api.HomeChromeConfig
import com.neoutils.finsight.feature.home.api.HomeChromeController

internal class HomeChromeStateHolder : HomeChromeController {
    var config by mutableStateOf(HomeChromeConfig.Default)
        private set

    override fun update(config: HomeChromeConfig) {
        this.config = config
    }

    override fun reset() {
        config = HomeChromeConfig.Default
    }
}

@Composable
internal fun rememberHomeChromeStateHolder(): HomeChromeStateHolder {
    return remember { HomeChromeStateHolder() }
}
