package com.neoutils.finance.di

import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.GetCategoriesUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory { AdjustBalanceUseCase(get(), get()) }
    factory { AdjustFinalBalanceUseCase(get()) }
    factory { AdjustInitialBalanceUseCase(get()) }
    factory { CalculateBalanceUseCase(get()) }
    factory { CalculateTransactionStatsUseCase() }
    factory { CalculateCategorySpendingUseCase() }
    factory { GetCategoriesUseCase(get()) }
}