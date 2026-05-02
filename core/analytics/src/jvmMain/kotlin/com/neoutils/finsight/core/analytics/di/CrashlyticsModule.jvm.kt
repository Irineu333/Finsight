package com.neoutils.finsight.core.analytics.di

import com.neoutils.finsight.core.analytics.crashlytics.NoOpCrashlytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import org.koin.dsl.module

internal actual val crashlyticsPlatformModule = module {
    single<Crashlytics> { NoOpCrashlytics() }
}
