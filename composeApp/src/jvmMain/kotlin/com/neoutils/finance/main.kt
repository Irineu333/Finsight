package com.neoutils.finance

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neoutils.finance.di.databaseModule
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(databaseModule)
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "finance",
    ) {
        App()
    }
}