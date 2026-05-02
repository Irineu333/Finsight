package com.neoutils.finsight.core.database.di

import com.neoutils.finsight.core.database.getDatabaseBuilder
import org.koin.dsl.module

internal actual val databasePlatformModule = module {
    single { getDatabaseBuilder(context = get()) }
}