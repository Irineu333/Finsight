package com.neoutils.finance.di

import com.neoutils.finance.database.getDatabaseBuilder
import org.koin.dsl.module

actual val databasePlatformModule = module {
    single { getDatabaseBuilder() }
}
