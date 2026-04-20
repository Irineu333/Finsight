package com.neoutils.finsight.database.di

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.database.dao.BudgetDao
import com.neoutils.finsight.database.dao.CategoryDao
import com.neoutils.finsight.database.dao.InstallmentDao
import com.neoutils.finsight.database.dao.InvoiceDao
import com.neoutils.finsight.database.dao.OperationDao
import com.neoutils.finsight.database.dao.RecurringDao
import com.neoutils.finsight.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.database.dao.TransactionDao
import com.neoutils.finsight.database.getRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<OperationDao> { get<AppDatabase>().operationDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<InvoiceDao> { get<AppDatabase>().invoiceDao() }
    single<AccountDao> { get<AppDatabase>().accountDao() }
    single<InstallmentDao> { get<AppDatabase>().installmentDao() }
    single<BudgetDao> { get<AppDatabase>().budgetDao() }
    single<RecurringDao> { get<AppDatabase>().recurringDao() }
    single<RecurringOccurrenceDao> { get<AppDatabase>().recurringOccurrenceDao() }
}

internal expect val databasePlatformModule: Module