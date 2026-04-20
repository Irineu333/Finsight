package com.neoutils.finsight.di

import com.neoutils.finsight.crashlytics.NoOpCrashlytics
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import org.koin.dsl.module

internal actual val crashlyticsPlatformModule = module {
    single<Crashlytics> { NoOpCrashlytics() }
}
