package com.neoutils.finsight

import android.app.Application
import com.neoutils.finsight.di.analyticsModule
import com.neoutils.finsight.di.crashlyticsModule
import com.neoutils.finsight.di.authModule
import com.neoutils.finsight.di.databaseModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.repositoryModule
import com.neoutils.finsight.di.viewModelModule
import com.neoutils.finsight.di.supportModule
import com.neoutils.finsight.di.creditCardsModule
import com.neoutils.finsight.di.categoriesModule
import com.neoutils.finsight.di.transactionsModule
import com.neoutils.finsight.di.accountsModule
import com.neoutils.finsight.di.budgetsModule
import com.neoutils.finsight.di.recurringModule
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
                repositoryModule,
                reportModule,
                analyticsModule,
                crashlyticsModule,
                authModule,
                viewModelModule,
                supportModule,
                categoriesModule,
                creditCardsModule,
                transactionsModule,
                accountsModule,
                budgetsModule,
                recurringModule,
            )
        }
    }
}
