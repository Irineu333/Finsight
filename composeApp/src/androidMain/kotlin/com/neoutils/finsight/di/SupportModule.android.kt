package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.FirebaseSupportRepository
import com.neoutils.finsight.domain.repository.ISupportRepository
import org.koin.dsl.module

actual val supportPlatformModule = module {
    single<ISupportRepository> { FirebaseSupportRepository(analytics = get(), crashlytics = get()) }
}