package com.neoutils.finance.di

import com.neoutils.finance.data.AppDatabase
import com.neoutils.finance.data.CategoryDao
import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionDao
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.data.getRoomDatabase
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
    single<TransactionRepository> {
        TransactionRepository(dao = get())
    }
    single<CategoryRepository> {
        CategoryRepository(dao = get())
    }

    factory { AdjustBalanceUseCase(get()) }
    factory { CalculateBalanceUseCase() }
    factory { CalculateTransactionStatsUseCase() }
    factory { GetCategoriesUseCase(get()) }

    viewModel {
        DashboardViewModel(
            repository = get(),
            categoryRepository = get(),
            adjustBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get()
        )
    }

    viewModel {
        TransactionsViewModel(
            repository = get(),
            categoryRepository = get(),
            adjustBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get()
        )
    }

    viewModel {
        CategoriesViewModel(
            getCategoriesUseCase = get()
        )
    }

    viewModel { parameters ->
        com.neoutils.finance.ui.modal.ViewCategoryViewModel(
            category = parameters.get(),
            repository = get()
        )
    }
}

expect val databasePlatformModule: Module
