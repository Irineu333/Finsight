package com.neoutils.finance.di

import com.neoutils.finance.data.repository.CategoryRepository
import com.neoutils.finance.data.repository.TransactionRepository
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import org.koin.dsl.module

val repositoryModule = module {

    single<ICategoryRepository> {
        CategoryRepository(
            dao = get(),
            mapper = get(),
        )
    }

    single<ITransactionRepository> {
        TransactionRepository(
            dao = get(),
            categoryRepository = get(),
        )
    }
}