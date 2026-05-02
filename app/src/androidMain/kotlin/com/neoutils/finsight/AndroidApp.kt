package com.neoutils.finsight

import android.app.Application
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
import com.neoutils.finsight.di.dashboardModule
import com.neoutils.finsight.feature.report.di.reportModule
import com.neoutils.finsight.di.supportModule
import com.neoutils.finsight.core.ui.di.uiModule
import com.neoutils.finsight.core.utils.util.di.utilsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin

class AndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            androidLogger()
            androidContext(this@AndroidApp)

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
}
