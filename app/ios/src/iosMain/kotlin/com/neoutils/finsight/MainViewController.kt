package com.neoutils.finsight

import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.ui.App
import org.koin.core.context.startKoin
import platform.UIKit.UIViewController

fun MainViewController(): UIViewController = ComposeUIViewController(
    configure = {
        startKoin {
            // Aggregated last: a debug build replaces a shipped definition by declaring it again.
            modules(appModules + debugModules)
        }

        // Before anything composes: whatever reads the clock must read the shifted one from the
        // start. A no-op outside a debug build.
        applyTimeTravel()
    }
) {
    App()
}
