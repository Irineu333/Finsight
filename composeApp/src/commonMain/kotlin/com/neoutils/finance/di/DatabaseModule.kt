package com.neoutils.finance.di

import com.neoutils.finance.data.AppDatabase
import com.neoutils.finance.data.database.CategoryDao
import com.neoutils.finance.data.database.TransactionDao
import com.neoutils.finance.data.getRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
}

expect val databasePlatformModule: Module
