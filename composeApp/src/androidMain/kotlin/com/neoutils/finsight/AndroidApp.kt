package com.neoutils.finsight

import android.app.Application
import com.neoutils.finsight.di.databaseModule
import com.neoutils.finsight.di.mapperModule
import com.neoutils.finsight.di.reportModule
import com.neoutils.finsight.di.repositoryModule
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
                mapperModule,
                repositoryModule,
                useCaseModules,
                reportModule,
                viewModelModule,
            )
        }
    }
}
