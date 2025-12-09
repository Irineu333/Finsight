@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.di

import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.modal.addCategory.AddCategoryViewModel
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finance.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finance.ui.modal.editBalance.EditBalanceViewModel
import com.neoutils.finance.ui.modal.editCategory.EditCategoryViewModel
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionViewModel
import com.neoutils.finance.ui.screen.categories.CategoriesViewModel
import com.neoutils.finance.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finance.ui.screen.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val viewModelModule = module {
    viewModel {
        ViewCategoryViewModel(
            category = it.get(),
            categoryRepository = get(),
            repository = get()
        )
    }

    viewModel {
        ViewTransactionViewModel(
            transaction = it.get(),
            transactionRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            repository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateCreditCardBillUseCase = get()
        )
    }

    viewModel {
        TransactionsViewModel(
            transaction = getOrNull(),
            category = getOrNull(),
            target = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }

    viewModel {
        CategoriesViewModel(
            getCategoriesUseCase = get()
        )
    }

    viewModel {
        AddTransactionViewModel(
            transactionRepository = get(),
            categoryRepository = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditTransactionViewModel(
            transactionRepository = get(),
            categoryRepository = get(),
            modalManager = get()
        )
    }

    viewModel {
        DeleteTransactionViewModel(
            transaction = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        AddCategoryViewModel(
            initialType = it.get(),
            repository = get(),
            getCategoriesUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditCategoryViewModel(
            category = it.get(),
            repository = get(),
            getCategoriesUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        DeleteCategoryViewModel(
            category = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditBalanceViewModel(
            type = it.get(),
            targetMonth = it.getOrNull() ?: Clock.System.now().toYearMonth(),
            adjustBalanceUseCase = get(),
            adjustFinalBalanceUseCase = get(),
            adjustInitialBalanceUseCase = get(),
            adjustCreditCardBillUseCase = get(),
            modalManager = get()
        )
    }
}