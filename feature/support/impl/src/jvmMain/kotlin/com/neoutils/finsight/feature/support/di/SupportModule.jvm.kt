package com.neoutils.finsight.feature.support.di

import com.neoutils.finsight.feature.support.UnsupportedSupportRepository
import com.neoutils.finsight.feature.support.repository.ISupportRepository
import org.koin.dsl.module

actual val supportPlatformModule = module {
    single<ISupportRepository> { UnsupportedSupportRepository() }
}
