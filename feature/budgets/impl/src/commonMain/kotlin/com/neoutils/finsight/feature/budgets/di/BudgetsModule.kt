package com.neoutils.finsight.feature.budgets.di

import com.neoutils.finsight.feature.budgets.mapper.BudgetMapper
import com.neoutils.finsight.feature.budgets.repository.BudgetRepository
import com.neoutils.finsight.feature.budgets.repository.IBudgetRepository
import com.neoutils.finsight.feature.budgets.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.feature.budgets.usecase.ICalculateBudgetProgressUseCase
import com.neoutils.finsight.feature.budgets.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.feature.budgets.modal.budgetForm.BudgetFormViewModel
import com.neoutils.finsight.feature.budgets.modal.viewBudget.ViewBudgetModalEntry
import com.neoutils.finsight.feature.budgets.modal.viewBudget.ViewBudgetModalEntryImpl
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
            categoryRepository = get(),
        )
    }

    factory<ICalculateBudgetProgressUseCase> { CalculateBudgetProgressUseCase() }
    factory { CalculateBudgetProgressUseCase() }

    factory {
        ValidateBudgetTitleUseCase(
            repository = get(),
        )
    }

    viewModel {
        BudgetsViewModel(
            budgetRepository = get(),
            operationRepository = get(),
            recurringRepository = get(),
            calculateBudgetProgressUseCase = get(),
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
}
