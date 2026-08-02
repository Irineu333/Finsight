package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.ExchangeRateMapper
import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.database.repository.ExchangeRateRepository
import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.model.SeededBaseCurrency
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormViewModel
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

    factory { ExchangeRateMapper() }
    single<IExchangeRateRepository> {
        ExchangeRateRepository(dao = get(), mapper = get(), baseCurrencyRepository = get())
    }

    viewModel { SettingsViewModel(baseCurrencyRepository = get()) }

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
            modalManager = get(),
        )
    }
}
