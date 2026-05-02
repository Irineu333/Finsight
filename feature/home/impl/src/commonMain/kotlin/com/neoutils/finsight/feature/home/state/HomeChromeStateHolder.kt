package com.neoutils.finsight.feature.home.state

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

class HomeChromeStateHolder : HomeChromeController {
    var config by mutableStateOf(HomeChromeConfig.Companion.Default)
        private set

    override fun update(config: HomeChromeConfig) {
        this.config = config
    }

    override fun reset() {
        config = HomeChromeConfig.Companion.Default
    }
}

@Composable
fun rememberHomeChromeStateHolder(): HomeChromeStateHolder {
    return remember { HomeChromeStateHolder() }
}
