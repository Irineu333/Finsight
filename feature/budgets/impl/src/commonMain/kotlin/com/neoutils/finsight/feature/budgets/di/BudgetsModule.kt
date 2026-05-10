package com.neoutils.finsight.feature.budgets.di

import com.neoutils.finsight.feature.budgets.mapper.BudgetMapper
import com.neoutils.finsight.feature.budgets.repository.BudgetRepository
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.budgets.usecase.GetBudgetProgressUseCase
import com.neoutils.finsight.feature.budgets.usecase.IGetBudgetProgressUseCase
import com.neoutils.finsight.feature.budgets.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.feature.budgets.modal.budgetForm.BudgetFormViewModel
import com.neoutils.finsight.feature.budgets.modal.viewBudget.ViewBudgetModalEntry
import com.neoutils.finsight.feature.budgets.modal.viewBudget.ViewBudgetModalEntryImpl
import com.neoutils.finsight.feature.budgets.modal.viewBudget.ViewBudgetViewModel
import com.neoutils.finsight.feature.budgets.modal.deleteBudget.DeleteBudgetViewModel
import com.neoutils.finsight.feature.budgets.screen.BudgetsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val budgetsModule = module {

    single<ViewBudgetModalEntry> { ViewBudgetModalEntryImpl() }

    factory { BudgetMapper() }

    single<IBudgetRepository> {
        BudgetRepository(
            dao = get(),
            mapper = get(),
        )
    }

    factory<IGetBudgetProgressUseCase> {
        GetBudgetProgressUseCase(
            budgetRepository = get(),
            transactionRepository = get(),
            operationRepository = get(),
            recurringRepository = get(),
        )
    }

    factory {
        ValidateBudgetTitleUseCase(
            repository = get(),
        )
    }

    viewModel {
        BudgetsViewModel(
            budgetRepository = get(),
            getBudgetProgress = get(),
        )
    }

    viewModel {
        BudgetFormViewModel(
            formatter = get(),
            budget = it.getOrNull(),
            budgetRepository = get(),
            categoryRepository = get(),
            recurringRepository = get(),
            validateBudgetTitle = get(),
            modalManager = get(),
            debounceManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        DeleteBudgetViewModel(
            budget = it.get(),
            budgetRepository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel { (budgetId: Long) ->
        ViewBudgetViewModel(
            budgetId = budgetId,
            categoryRepository = get(),
            getBudgetProgress = get(),
            budgetRepository = get(),
            crashlytics = get(),
        )
    }
}
