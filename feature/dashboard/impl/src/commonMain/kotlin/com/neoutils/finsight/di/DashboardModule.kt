package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.domain.model.ArchiveReplacedHook
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.usecase.BuildDashboardViewingUseCase
import com.neoutils.finsight.domain.usecase.DashboardArchiveReplacedHook
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

    // Claimed here, and here only: the three preferences that name an account or a card
    // by database id are this feature's own, so forgetting them once a restore has
    // replaced the archive is this feature's to decide, not `ArchiveRestore`'s (design
    // analogous to `TransactionRemovalPrelude` — a restore proceeds correctly whether or
    // not anybody claims this).
    single<ArchiveReplacedHook> { DashboardArchiveReplacedHook(repository = get()) }

    factory {
        DashboardComponentsBuilder(
            calculateBalanceUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateCategoryIncomeUseCase = get(),
            calculateBudgetProgressUseCase = get(),
            getPendingRecurringUseCase = get(),
            getUnhandledRecurringUseCase = get(),
            invoiceUiMapper = get(),
            calculateAvailableLimit = get(),
            entryRepository = get(),
            accountRepository = get(),
            consolidateMoney = get(),
            navCatalog = get(),
        )
    }

    single { GetDashboardPreferencesUseCase(get(), get()) }
    factory { BuildDashboardViewingUseCase(get()) }
    single {
        DashboardPreviewFactory(
            consolidateMoney = get(),
            navCatalog = get(),
            baseCurrencyRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            transactionRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            categoryRepository = get(),
            installmentRepository = get(),
            ensureDefaultAccountUseCase = get(),
            getDashboardPreferences = get(),
            buildDashboardViewingUseCase = get(),
            dashboardPreferencesRepository = get(),
            observeConsolidationChanges = get(),
            dashboardPreviewFactory = get(),
            baseCurrencyRepository = get(),
            analytics = get(),
            crashlytics = get(),
            clock = get(),
        )
    }
}
