package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.HarvestExchangeRateUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import org.koin.dsl.module

/**
 * The consolidation layer: the one reducer, the rate it harvests from a crossing, and
 * the trigger a consolidated figure recomputes on.
 *
 * The two repositories these depend on are declared in this module's `domain/repository`
 * but **implemented** by the settings feature, and bound by the shell. That split is a
 * compilation constraint, not taste: `feature/accounts/api` and this module both need
 * the base currency, and `api ⊄ api` while `core ⊄ feature`.
 */
val modelModule = module {
    factory { ConsolidateMoneyUseCase(baseCurrencyRepository = get(), exchangeRateRepository = get()) }
    factory { HarvestExchangeRateUseCase(baseCurrencyRepository = get(), exchangeRateRepository = get()) }
    factory { SuggestCrossCurrencyAmountUseCase(baseCurrencyRepository = get(), exchangeRateRepository = get()) }
    factory {
        ObserveConsolidationChangesUseCase(
            entryRepository = get(),
            baseCurrencyRepository = get(),
            exchangeRateRepository = get(),
        )
    }
}
