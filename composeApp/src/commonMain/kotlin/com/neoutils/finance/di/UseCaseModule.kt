package com.neoutils.finance.di

import com.neoutils.finance.usecase.AdjustBalanceUseCase
import com.neoutils.finance.usecase.CalculateBalanceUseCase
import com.neoutils.finance.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.usecase.GetCategoriesUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory { AdjustBalanceUseCase(get()) }
    factory { CalculateBalanceUseCase() }
    factory { CalculateTransactionStatsUseCase() }
    factory { GetCategoriesUseCase(get()) }
}