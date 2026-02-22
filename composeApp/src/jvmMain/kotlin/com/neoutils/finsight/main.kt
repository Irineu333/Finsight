package com.neoutils.finsight

import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neoutils.finsight.di.databaseModule
import com.neoutils.finsight.di.mapperModule
import com.neoutils.finsight.di.repositoryModule
import com.neoutils.finsight.di.useCaseModules
import com.neoutils.finsight.di.viewModelModule
import org.koin.core.context.startKoin

fun main() = application {
    startKoin {
        modules(
            databaseModule,
            mapperModule,
            repositoryModule,
            useCaseModules,
            viewModelModule,
        )
    }

    Window(
        onCloseRequest = ::exitApplication,
        title = "Finsight",
    ) {
        App()
    }
}