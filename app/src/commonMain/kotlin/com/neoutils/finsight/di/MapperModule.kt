package com.neoutils.finsight.di

import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import org.koin.dsl.module

val mapperModule = module {
    factory {
        InvoiceUiMapper(
            calculateInvoiceUseCase = get(),
            calculateAvailableLimitUseCase = get(),
        )
    }
}
