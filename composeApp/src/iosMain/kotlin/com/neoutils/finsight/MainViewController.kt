package com.neoutils.finsight

import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.di.databaseModule
import com.neoutils.finsight.di.mapperModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.repositoryModule
import com.neoutils.finsight.di.useCaseModules
import com.neoutils.finsight.di.viewModelModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(
                databaseModule,
                mapperModule,
                repositoryModule,
                useCaseModules,
                reportModule,
                viewModelModule,
            )
        }
    }
) {
    App()
}
