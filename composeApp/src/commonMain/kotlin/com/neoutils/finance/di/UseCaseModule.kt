package com.neoutils.finance.di

import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.GetCategoriesUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory { AdjustBalanceUseCase(get()) }
    factory { CalculateBalanceUseCase() }
    factory { CalculateTransactionStatsUseCase() }
    factory { GetCategoriesUseCase(get()) }
}