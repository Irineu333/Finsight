package com.neoutils.finance.di

import com.neoutils.finance.database.mapper.CategoryMapper
import com.neoutils.finance.database.mapper.CreditCardMapper
import com.neoutils.finance.database.mapper.TransactionMapper
import com.neoutils.finance.ui.mapper.CreditCardBillUiMapper
import org.koin.dsl.module

val mapperModule = module {
    factory { CategoryMapper() }
    factory { CreditCardMapper() }
    factory { TransactionMapper() }
    factory { CreditCardBillUiMapper() }
}