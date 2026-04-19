package com.neoutils.finsight

import android.app.Application
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
}
