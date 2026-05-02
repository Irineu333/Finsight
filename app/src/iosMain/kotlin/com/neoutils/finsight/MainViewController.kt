package com.neoutils.finsight

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.ComposeUIViewController
import com.neoutils.finsight.database.di.databaseModule
import com.neoutils.finsight.di.accountsModule
import com.neoutils.finsight.di.categoriesModule
import com.neoutils.finsight.di.creditCardsModule
import com.neoutils.finsight.di.transactionsModule
import com.neoutils.finsight.di.recurringModule
import com.neoutils.finsight.di.installmentsModule
import com.neoutils.finsight.di.budgetsModule
import com.neoutils.finsight.di.analyticsModule
import com.neoutils.finsight.di.authModule
import com.neoutils.finsight.di.crashlyticsModule
import com.neoutils.finsight.di.dashboardModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.supportModule
import com.neoutils.finsight.ui.di.uiModule
import com.neoutils.finsight.util.di.utilsModule
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
                    utilsModule,
                    uiModule,
                    supportModule,
                    accountsModule,
                    categoriesModule,
                    creditCardsModule,
                    transactionsModule,
                    recurringModule,
                    installmentsModule,
                    budgetsModule,
                    dashboardModule,
                    reportModule,
                    analyticsModule,
                    crashlyticsModule,
                    authModule,
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
