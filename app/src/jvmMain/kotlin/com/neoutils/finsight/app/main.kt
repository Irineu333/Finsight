package com.neoutils.finsight.app

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import com.neoutils.finsight.core.database.di.databaseModule
import com.neoutils.finsight.feature.accounts.di.accountsModule
import com.neoutils.finsight.feature.categories.di.categoriesModule
import com.neoutils.finsight.feature.creditCards.di.creditCardsModule
import com.neoutils.finsight.feature.transactions.di.transactionsModule
import com.neoutils.finsight.feature.recurring.di.recurringModule
import com.neoutils.finsight.feature.installments.di.installmentsModule
import com.neoutils.finsight.feature.budgets.di.budgetsModule
import com.neoutils.finsight.core.analytics.di.analyticsModule
import com.neoutils.finsight.core.auth.di.authModule
import com.neoutils.finsight.core.analytics.di.crashlyticsModule
import com.neoutils.finsight.feature.dashboard.di.dashboardModule
import com.neoutils.finsight.feature.report.di.reportModule
import com.neoutils.finsight.feature.support.di.supportModule
import com.neoutils.finsight.core.ui.di.uiModule
import com.neoutils.finsight.core.utils.util.di.utilsModule
import com.neoutils.finsight.core.ui.extension.LocalPlatformContext
import com.neoutils.finsight.core.ui.extension.PlatformContext
import com.neoutils.finsight.app.screen.root.App
import org.koin.core.context.startKoin

fun main() = application {
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

    Window(
        onCloseRequest = ::exitApplication,
        title = "Finsight",
    ) {
        CompositionLocalProvider(LocalPlatformContext provides PlatformContext(this)) {
            App()
        }
    }
}
