package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.database.repository.BudgetRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CreateBudgetUseCase
import com.neoutils.finsight.domain.usecase.CreateBudgetUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteBudgetUseCase
import com.neoutils.finsight.domain.usecase.DeleteBudgetUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateBudgetUseCase
import com.neoutils.finsight.domain.usecase.UpdateBudgetUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.feature.budgets.api.BudgetsEntry
import com.neoutils.finsight.feature.budgets.impl.BudgetsEntryImpl
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormViewModel
import com.neoutils.finsight.ui.modal.deleteBudget.DeleteBudgetViewModel
import com.neoutils.finsight.ui.modal.viewBudget.ViewBudgetViewModel
import com.neoutils.finsight.ui.screen.budgets.BudgetsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val budgetsModule = module {
    single<IBudgetRepository> {
        BudgetRepository(
            dao = get(),
            mapper = get(),
            categoryRepository = get(),
        )
    }
    factory { BudgetMapper() }

    factory { CalculateBudgetProgressUseCase(entryRepository = get(), consolidateMoney = get()) }
    factory { ValidateBudgetTitleUseCase(repository = get()) }
    factory<CreateBudgetUseCase> {
        CreateBudgetUseCaseImpl(
            budgetRepository = get(),
            validateBudgetTitle = get(),
        )
    }
    factory<UpdateBudgetUseCase> {
        UpdateBudgetUseCaseImpl(
            budgetRepository = get(),
            validateBudgetTitle = get(),
        )
    }
    factory<DeleteBudgetUseCase> {
        DeleteBudgetUseCaseImpl(
            budgetRepository = get(),
        )
    }

    single<BudgetsEntry> { BudgetsEntryImpl() }

    viewModel {
        BudgetsViewModel(
            budgetRepository = get(),
            transactionRepository = get(),
            recurringRepository = get(),
            calculateBudgetProgressUseCase = get(),
            observeConsolidationChanges = get(),
        )
    }
    viewModel {
        ViewBudgetViewModel(
            budgetId = it.get(),
            month = it.get(),
            budgetRepository = get(),
            transactionRepository = get(),
            recurringRepository = get(),
            calculateBudgetProgressUseCase = get(),
            observeConsolidationChanges = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        BudgetFormViewModel(
            formatter = get(),
            budget = it.getOrNull(),
            createBudget = get(),
            updateBudget = get(),
            getAccountCurrencies = get(),
            currencyRepository = get(),
            categoryRepository = get(),
            recurringRepository = get(),
            validateBudgetTitle = get(),
            modalManager = get(),
            debounceManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        DeleteBudgetViewModel(
            budget = it.get(),
            deleteBudgetUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
