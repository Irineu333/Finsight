@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.dashboard.di

import com.neoutils.finsight.feature.dashboard.repository.DashboardPreferencesRepository
import com.neoutils.finsight.feature.dashboard.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.feature.dashboard.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.feature.dashboard.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.feature.dashboard.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.feature.dashboard.usecase.GetDashboardPreferencesUseCase
import com.neoutils.finsight.feature.dashboard.builder.DashboardComponentsBuilder
import com.neoutils.finsight.feature.dashboard.screen.DashboardEntry
import com.neoutils.finsight.feature.dashboard.factory.DashboardPreviewFactory
import com.neoutils.finsight.feature.dashboard.screen.DashboardViewModel
import com.neoutils.finsight.feature.dashboard.entry.DashboardEntryImpl
import com.russhwolf.settings.Settings
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.ExperimentalTime

val dashboardModule = module {

    single<DashboardEntry> { DashboardEntryImpl() }

    single<Settings> { Settings() }

    single<IDashboardPreferencesRepository> {
        DashboardPreferencesRepository(
            settings = get(),
        )
    }

    factory { CalculateCategorySpendingUseCase() }

    factory { CalculateCategoryIncomeUseCase() }

    factory {
        DashboardComponentsBuilder(
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateCategoryIncomeUseCase = get(),
            getBudgetProgress = get(),
            getPendingRecurringUseCase = get(),
            invoiceUiMapper = get(),
            operationUiMapper = get(),
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
            categoryRepository = get(),
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
