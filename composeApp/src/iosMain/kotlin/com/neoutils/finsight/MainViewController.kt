package com.neoutils.finsight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.database.di.databaseModule
import com.neoutils.finsight.di.analyticsModule
import com.neoutils.finsight.di.authModule
import com.neoutils.finsight.di.crashlyticsModule
import com.neoutils.finsight.di.mapperModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.repositoryModule
import com.neoutils.finsight.di.supportPlatformModule
import com.neoutils.finsight.di.useCaseModules
import com.neoutils.finsight.di.viewModelModule
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
                modules(
                    databaseModule,
                    supportPlatformModule,
                    mapperModule,
                    repositoryModule,
                    useCaseModules,
                    reportModule,
                    analyticsModule,
                    crashlyticsModule,
                    authModule,
                    viewModelModule,
                )
            }
        }
    ) {
        CompositionLocalProvider(LocalPlatformContext provides PlatformContext(vc)) {
            App()
        }
    }
    return vc
}
