package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import org.koin.dsl.module

val dashboardModule = module {

    single<IDashboardPreferencesRepository> {
        DashboardPreferencesRepository(
            settings = get(),
        )
    }

    factory { CalculateCategorySpendingUseCase() }

    factory { CalculateCategoryIncomeUseCase() }
}
