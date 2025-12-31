package com.neoutils.finance

import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finance.di.databaseModule
import com.neoutils.finance.di.mapperModule
import com.neoutils.finance.di.repositoryModule
import com.neoutils.finance.di.useCaseModules
import com.neoutils.finance.di.viewModelModule
import org.koin.core.context.startKoin

fun MainViewController() = ComposeUIViewController(
    configure = {
        startKoin {
            modules(
                databaseModule,
                mapperModule,
                repositoryModule,
                useCaseModules,
                viewModelModule,
            )
        }
    }
) {
    App()
}
