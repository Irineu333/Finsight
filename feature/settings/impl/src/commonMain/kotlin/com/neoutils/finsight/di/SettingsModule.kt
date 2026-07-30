package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.BaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.usecase.ResolveBaseCurrencyUseCase
import com.neoutils.finsight.ui.modal.exchangeRateForm.ExchangeRateFormViewModel
import com.neoutils.finsight.ui.screen.exchangeRates.ExchangeRatesViewModel
import com.neoutils.finsight.ui.screen.settings.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val settingsModule = module {

    factory { ResolveBaseCurrencyUseCase() }

    // `single`, and eagerly resolved in construction: the base has to be settled before the
    // first figure is rendered, and resolving it twice would risk two answers.
    single<IBaseCurrencyRepository> {
        BaseCurrencyRepository(
            settings = get(),
            resolveBaseCurrency = get(),
        )
    }

    viewModel {
        SettingsViewModel(
            baseCurrencyRepository = get(),
        )
    }

    viewModel {
        ExchangeRatesViewModel(
            exchangeRateRepository = get(),
            baseCurrencyRepository = get(),
        )
    }

    viewModel {
        ExchangeRateFormViewModel(
            rate = it.getOrNull(),
            base = it.get(),
            exchangeRateRepository = get(),
            modalManager = get(),
        )
    }
}
