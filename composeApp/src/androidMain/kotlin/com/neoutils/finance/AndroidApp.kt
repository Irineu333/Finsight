package com.neoutils.finance

import android.app.Application
import com.neoutils.finance.di.databaseModule
import com.neoutils.finance.di.repositoryModule
import com.neoutils.finance.di.useCaseModules
import com.neoutils.finance.di.viewModelModule
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
                useCaseModules,
                viewModelModule,
            )
        }
    }
}