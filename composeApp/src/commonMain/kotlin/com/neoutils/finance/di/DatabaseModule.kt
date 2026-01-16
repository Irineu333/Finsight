package com.neoutils.finance.di

import com.neoutils.finance.database.AppDatabase
import com.neoutils.finance.database.dao.AccountDao
import com.neoutils.finance.database.dao.CategoryDao
import com.neoutils.finance.database.dao.InvoiceDao
import com.neoutils.finance.database.dao.TransactionDao
import com.neoutils.finance.database.getRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<InvoiceDao> { get<AppDatabase>().invoiceDao() }
    single<AccountDao> { get<AppDatabase>().accountDao() }
}

expect val databasePlatformModule: Module

