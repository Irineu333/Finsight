package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory { CalculateCategorySpendingUseCase() }

    factory { CalculateCategoryIncomeUseCase() }
}
