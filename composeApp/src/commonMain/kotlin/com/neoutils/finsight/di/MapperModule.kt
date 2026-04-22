package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.database.mapper.OperationMapper
import com.neoutils.finsight.ui.mapper.InstallmentUiMapper
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import org.koin.dsl.module

val mapperModule = module {
    factory { BudgetMapper() }
    factory { OperationMapper() }
    factory { InstallmentUiMapper() }
    factory {
        InvoiceUiMapper(
            calculateInvoiceUseCase = get(),
            calculateAvailableLimitUseCase = get(),
        )
    }
}
