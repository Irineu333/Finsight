package com.neoutils.finsight.database.di

import com.neoutils.finsight.database.getDatabaseBuilder
import org.koin.dsl.module

actual val databasePlatformModule = module {
    single { getDatabaseBuilder(context = get()) }
}