@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.domain.usecase.GetDashboardPreferencesUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.screen.dashboard.DashboardComponentsBuilder
import com.neoutils.finsight.ui.screen.dashboard.DashboardPreviewFactory
import com.neoutils.finsight.ui.screen.dashboard.DashboardViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val viewModelModule = module {

    factory {
        DashboardComponentsBuilder(
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateCategoryIncomeUseCase = get(),
            calculateBudgetProgressUseCase = get(),
            getPendingRecurringUseCase = get(),
            invoiceUiMapper = get(),
        )
    }

    single { GetDashboardPreferencesUseCase(get()) }
    factory { BuildDashboardViewingUseCase(get()) }

    single { DashboardPreviewFactory() }

    viewModel {
        DashboardViewModel(
            operationRepository = get(),
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
