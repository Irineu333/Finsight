package com.neoutils.finsight

import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.ui.screen.root.App
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = {
        startKoin {
            modules(appModules)
        }
    }
) {
    App()
}
