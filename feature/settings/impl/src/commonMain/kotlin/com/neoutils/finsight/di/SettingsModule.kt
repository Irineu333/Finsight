package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.database.repository.CurrencyRepository
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.database.repository.RepositoryCurrencySymbols
import com.neoutils.finsight.domain.model.CurrencySeeding
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.PlatformCurrencySeeding
import com.neoutils.finsight.domain.model.SeededBaseCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.ArchiveCurrencyUseCase
import com.neoutils.finsight.domain.usecase.DeleteCurrencyUseCase
import com.neoutils.finsight.domain.usecase.SaveCurrencyUseCase
import com.neoutils.finsight.extension.CurrencySymbols
import com.neoutils.finsight.domain.model.CurrencyInfo
import com.neoutils.finsight.ui.modal.currencyForm.CurrencyFormViewModel
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormViewModel
import com.neoutils.finsight.ui.screen.currencies.CurrenciesViewModel
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesViewModel
import com.neoutils.finsight.ui.screen.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

/**
 * The settings feature owns the **implementations** of the two repositories the
 * consolidation layer declares in `:core:model`. The split is a compilation
 * constraint: `feature/accounts/api` and `:core:model` both need the base currency,
 * and `api ⊄ api` while `core ⊄ feature`. The shell binds this module, so the star
 * topology holds.
 */
val settingsModule = module {

    single<IBaseCurrencyRepository> { BaseCurrencyRepository(settings = get()) }

    // The migration's backfill, resolved where the preference is visible and asked for
    // by `core/database`, which may name neither `Settings` nor the repository. There is
    // no DI cycle: `BaseCurrencyRepository` depends only on `Settings`, never on
    // `AppDatabase`, and that is what lets the migration read it while it is opening.
    single<SeededBaseCurrency> { SeededBaseCurrency { get<IBaseCurrencyRepository>().observe().value } }

    // The seeding, resolved where the platform and the locale are visible and asked
    // for by `core/database`, which may name neither — the same move `SeededBaseCurrency`
    // above already makes, and for the same reason.
    single<CurrencySeeding> { PlatformCurrencySeeding() }

    single<ICurrencyRepository> { CurrencyRepository(dao = get()) }

    // The port of `:core:common`: the only binding where the table and the composition
    // local meet, so that `:core:designsystem` never has to see `:core:model`.
    single<CurrencySymbols> { RepositoryCurrencySymbols(repository = get()) }

    factory { SaveCurrencyUseCase(repository = get()) }
    factory {
        DeleteCurrencyUseCase(
            repository = get(),
            exchangeRateRepository = get(),
            accountDao = get(),
            budgetDao = get(),
        )
    }
    factory {
        ArchiveCurrencyUseCase(
            repository = get(),
            baseCurrencyRepository = get(),
        )
    }

    factory { ExchangeRateMapper() }
    single<IExchangeRateRepository> {
        ExchangeRateRepository(dao = get(), mapper = get(), baseCurrencyRepository = get())
    }

    viewModel { SettingsViewModel(baseCurrencyRepository = get(), currencyRepository = get()) }

    viewModel {
        ExchangeRatesViewModel(
            baseCurrencyRepository = get(),
            exchangeRateRepository = get(),
        )
    }

    viewModel {
        ExchangeRateFormViewModel(
            existing = it.getOrNull<ExchangeRate>(),
            baseCurrencyRepository = get(),
            exchangeRateRepository = get(),
            currencyRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        CurrenciesViewModel(
            currencyRepository = get(),
            archiveCurrency = get(),
            deleteCurrency = get(),
            baseCurrencyRepository = get(),
        )
    }

    viewModel {
        CurrencyFormViewModel(
            existing = it.getOrNull<CurrencyInfo>(),
            saveCurrency = get(),
            modalManager = get(),
        )
    }
}
