package com.neoutils.finsight.core.database.di

import com.neoutils.finsight.core.database.AppDatabase
import com.neoutils.finsight.core.database.dao.AccountDao
import com.neoutils.finsight.core.database.dao.BudgetDao
import com.neoutils.finsight.core.database.dao.CategoryDao
import com.neoutils.finsight.core.database.dao.CreditCardDao
import com.neoutils.finsight.core.database.dao.InstallmentDao
import com.neoutils.finsight.core.database.dao.InvoiceDao
import com.neoutils.finsight.core.database.dao.OperationDao
import com.neoutils.finsight.core.database.dao.RecurringDao
import com.neoutils.finsight.core.database.dao.RecurringOccurrenceDao
import com.neoutils.finsight.core.database.dao.TransactionDao
import com.neoutils.finsight.core.database.getRoomDatabase
import org.koin.core.module.Module
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<OperationDao> { get<AppDatabase>().operationDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
    single<CreditCardDao> { get<AppDatabase>().creditCardDao() }
    single<InvoiceDao> { get<AppDatabase>().invoiceDao() }
    single<AccountDao> { get<AppDatabase>().accountDao() }
    single<InstallmentDao> { get<AppDatabase>().installmentDao() }
    single<BudgetDao> { get<AppDatabase>().budgetDao() }
    single<RecurringDao> { get<AppDatabase>().recurringDao() }
    single<RecurringOccurrenceDao> { get<AppDatabase>().recurringOccurrenceDao() }
}

internal expect val databasePlatformModule: Module