package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.database.repository.BudgetRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormViewModel
import com.neoutils.finsight.ui.modal.deleteBudget.DeleteBudgetViewModel
import com.neoutils.finsight.ui.screen.budgets.BudgetsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val budgetsModule = module {

    factory { BudgetMapper() }

    single<IBudgetRepository> {
        BudgetRepository(
            dao = get(),
            mapper = get(),
            categoryRepository = get(),
        )
    }

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
