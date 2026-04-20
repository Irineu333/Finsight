package com.neoutils.finsight

import android.app.Application
import com.neoutils.finsight.database.di.databaseModule
import com.neoutils.finsight.di.accountsModule
import com.neoutils.finsight.di.categoriesModule
import com.neoutils.finsight.di.analyticsModule
import com.neoutils.finsight.di.authModule
import com.neoutils.finsight.di.crashlyticsModule
import com.neoutils.finsight.di.mapperModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.repositoryModule
import com.neoutils.finsight.di.supportPlatformModule
import com.neoutils.finsight.di.useCaseModules
import com.neoutils.finsight.di.viewModelModule
import com.neoutils.finsight.ui.di.uiModule
import com.neoutils.finsight.util.di.utilsModule
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
                supportPlatformModule,
                mapperModule,
                accountsModule,
                categoriesModule,
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
}
