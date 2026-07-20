package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.domain.usecase.GetDashboardPreferencesUseCase
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentsBuilder
import com.neoutils.finsight.ui.screen.dashboard.DashboardPreviewFactory
import com.neoutils.finsight.ui.screen.dashboard.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val dashboardModule = module {
    single<IDashboardPreferencesRepository> {
        DashboardPreferencesRepository(
            settings = get(),
        )
    }

    factory {
        DashboardComponentsBuilder(
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateCategoryIncomeUseCase = get(),
            calculateBudgetProgressUseCase = get(),
            getPendingRecurringUseCase = get(),
            invoiceUiMapper = get(),
            entryRepository = get(),
            navCatalog = get(),
        )
    }

    single { GetDashboardPreferencesUseCase(get(), get()) }
    factory { BuildDashboardViewingUseCase(get()) }
    single { DashboardPreviewFactory(get()) }

    viewModel {
        DashboardViewModel(
            transactionRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            ensureDefaultAccountUseCase = get(),
            getDashboardPreferences = get(),
            buildDashboardViewingUseCase = get(),
            dashboardPreferencesRepository = get(),
            dashboardPreviewFactory = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
