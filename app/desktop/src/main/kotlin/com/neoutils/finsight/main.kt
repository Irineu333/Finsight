package com.neoutils.finsight

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neoutils.finsight.di.appModules
import com.neoutils.finsight.extension.ProvideWindowScope
import com.neoutils.finsight.ui.App
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(appModules)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Finsight",
    ) {
        ProvideWindowScope {
            App()
        }
    }
}
