package com.neoutils.finsight.di

import com.neoutils.finsight.database.getDatabaseBuilder
import com.neoutils.finsight.database.repository.FirebaseSupportRepository
import com.neoutils.finsight.domain.repository.ISupportRepository
import org.koin.dsl.module

actual val databasePlatformModule = module {
    single { getDatabaseBuilder(context = get()) }

    single<ISupportRepository> { FirebaseSupportRepository() }
}