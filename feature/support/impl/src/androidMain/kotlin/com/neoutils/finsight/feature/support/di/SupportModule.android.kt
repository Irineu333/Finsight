package com.neoutils.finsight.feature.support.di

import com.neoutils.finsight.feature.support.repository.FirebaseSupportRepository
import com.neoutils.finsight.feature.support.repository.ISupportRepository
import org.koin.dsl.module

actual val supportPlatformModule = module {
    single<ISupportRepository> { FirebaseSupportRepository(analytics = get(), crashlytics = get()) }
}