package com.neoutils.finsight.di

import com.neoutils.finsight.database.UnsupportedSupportRepository
import com.neoutils.finsight.feature.support.api.ISupportRepository
import org.koin.core.module.Module
import org.koin.dsl.module

actual val supportPlatformModule: Module = module {
    single<ISupportRepository> { UnsupportedSupportRepository() }
}
