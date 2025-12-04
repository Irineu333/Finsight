package com.neoutils.finance.di

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionViewModel
import com.neoutils.finance.ui.screen.categories.CategoriesViewModel
import com.neoutils.finance.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finance.ui.screen.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {
    viewModel { (category: Category) ->
        ViewCategoryViewModel(
            category = category,
            categoryRepository = get(),
            repository = get()
        )
    }

    viewModel { (transaction: Transaction) ->
        ViewTransactionViewModel(
            transaction = transaction,
            transactionRepository = get(),
            categoryRepository = get()
        )
    }

    viewModel {
        DashboardViewModel(
            repository = get(),
            adjustBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get()
        )
    }

    viewModel {
        TransactionsViewModel(
            category = getOrNull(),
            transaction = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            adjustBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }

    viewModel {
        CategoriesViewModel(
            getCategoriesUseCase = get()
        )
    }
}