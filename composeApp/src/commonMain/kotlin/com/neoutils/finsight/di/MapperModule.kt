package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.AccountMapper
import com.neoutils.finsight.database.mapper.BudgetMapper
import com.neoutils.finsight.database.mapper.CategoryMapper
import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import org.koin.dsl.module

val mapperModule = module {
    factory { AccountMapper() }
    factory { BudgetMapper() }
    factory { CategoryMapper() }
    factory { CreditCardMapper() }
    factory { InvoiceMapper() }
    factory { TransactionMapper() }
    factory {
        InvoiceUiMapper(
            calculateInvoiceUseCase = get(),
            calculateAvailableLimitUseCase = get(),
        )
    }
}