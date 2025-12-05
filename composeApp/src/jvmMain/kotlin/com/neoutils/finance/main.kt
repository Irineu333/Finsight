package com.neoutils.finance

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neoutils.finance.di.databaseModule
import com.neoutils.finance.di.mapperModule
import com.neoutils.finance.di.repositoryModule
import com.neoutils.finance.di.uiModule
import com.neoutils.finance.di.useCaseModules
import com.neoutils.finance.di.viewModelModule
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(
            databaseModule,
            mapperModule,
            repositoryModule,
            useCaseModules,
            viewModelModule,
            uiModule,
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "finance",
    ) {
        App()
    }
}