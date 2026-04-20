package com.neoutils.finsight.di

import com.neoutils.finsight.analytics.FirebaseAnalyticsImpl
import com.neoutils.finsight.domain.analytics.Analytics
import org.koin.dsl.module

internal actual val analyticsPlatformModule = module {
    single<Analytics> { FirebaseAnalyticsImpl() }
}
