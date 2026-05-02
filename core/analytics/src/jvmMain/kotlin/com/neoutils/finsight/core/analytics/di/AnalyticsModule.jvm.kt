package com.neoutils.finsight.core.analytics.di

import com.neoutils.finsight.core.analytics.NoOpAnalytics
import com.neoutils.finsight.core.analytics.Analytics
import org.koin.dsl.module

internal actual val analyticsPlatformModule = module {
    single<Analytics> { NoOpAnalytics() }
}
