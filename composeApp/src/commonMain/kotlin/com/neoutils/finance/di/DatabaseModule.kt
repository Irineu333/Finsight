package com.neoutils.finance.di

import com.neoutils.finance.data.AppDatabase
import com.neoutils.finance.data.CategoryDao
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionDao
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.data.getRoomDatabase
import com.neoutils.finance.ui.modal.ViewCategoryViewModel
import com.neoutils.finance.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finance.ui.screen.transactions.TransactionsViewModel
import com.neoutils.finance.ui.screen.categories.CategoriesViewModel
import com.neoutils.finance.usecase.AdjustBalanceUseCase
import com.neoutils.finance.usecase.CalculateBalanceUseCase
import com.neoutils.finance.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.usecase.GetCategoriesUseCase
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val databaseModule = module {
    includes(databasePlatformModule)

    single<AppDatabase> { getRoomDatabase(builder = get()) }
    single<TransactionDao> { get<AppDatabase>().transactionDao() }
    single<CategoryDao> { get<AppDatabase>().categoryDao() }
}

expect val databasePlatformModule: Module
