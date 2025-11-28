package com.neoutils.finance.di

import com.neoutils.finance.data.AppDatabase
import com.neoutils.finance.data.TransactionDao
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.data.getRoomDatabase
import com.neoutils.finance.screen.dashboard.DashboardViewModel
import com.neoutils.finance.screen.transactions.TransactionsViewModel
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<TransactionRepository> {
        TransactionRepository(dao = get())
    }

    viewModel {
        DashboardViewModel(repository = get())
    }

    viewModel {
        TransactionsViewModel(repository = get())
    }
}

expect val databasePlatformModule: Module
