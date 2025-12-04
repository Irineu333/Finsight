package com.neoutils.finance.di

import com.neoutils.finance.database.mapper.CategoryMapper
import com.neoutils.finance.database.mapper.TransactionMapper
import org.koin.dsl.module

val mapperModule = module {
    single {
        CategoryMapper()
    }

    single {
        TransactionMapper()
    }
}