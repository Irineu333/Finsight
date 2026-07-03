package com.neoutils.finsight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.extension.LocalPlatformContext
import com.neoutils.finsight.extension.PlatformContext
import com.neoutils.finsight.ui.screen.root.App
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController {
    lateinit var vc: UIViewController
    vc = ComposeUIViewController(
        configure = {
            startKoin {
                modules(appModules)
            }
        }
    ) {
        CompositionLocalProvider(LocalPlatformContext provides PlatformContext(vc)) {
            App()
        }
    }
    return vc
}
