package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.ui.mapper.InstallmentUiMapper
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.mapper.InvoiceUiMapperImpl
import org.koin.dsl.module

val mapperModule = module {
    factory { CreditCardMapper() }
    factory { InvoiceMapper() }
    factory { InstallmentUiMapper() }
    factory<InvoiceUiMapper> {
        InvoiceUiMapperImpl(
            calculateInvoiceUseCase = get(),
            calculateAvailableLimitUseCase = get(),
        )
    }
}
