package com.neoutils.finance.di

import com.neoutils.finance.database.mapper.CategoryMapper
import com.neoutils.finance.database.mapper.CreditCardMapper
import com.neoutils.finance.database.mapper.InvoiceMapper
import com.neoutils.finance.database.mapper.TransactionMapper
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import org.koin.dsl.module

val mapperModule = module {
    factory { CategoryMapper() }
    factory { CreditCardMapper() }
    factory { InvoiceMapper() }
    factory { TransactionMapper() }
    factory { InvoiceUiMapper(calculateInvoiceUseCase = get()) }
}