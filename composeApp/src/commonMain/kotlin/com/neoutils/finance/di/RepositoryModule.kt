package com.neoutils.finance.di

import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<TransactionRepository> {
        TransactionRepository(dao = get())
    }
    single<CategoryRepository> {
        CategoryRepository(dao = get())
    }
}