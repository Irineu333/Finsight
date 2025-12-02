package com.neoutils.finance.di

import com.neoutils.finance.data.CategoryRepository
import com.neoutils.finance.data.TransactionRepository
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import org.koin.dsl.module

val repositoryModule = module {
    single<ITransactionRepository> {
        TransactionRepository(dao = get())
    }
    single<ICategoryRepository> {
        CategoryRepository(dao = get())
    }
}