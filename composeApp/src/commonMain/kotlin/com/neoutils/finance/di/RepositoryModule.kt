package com.neoutils.finance.di

import com.neoutils.finance.data.repository.PreferencesRepository
import com.neoutils.finance.data.repository.PreferencesRepositoryImpl
import com.neoutils.finance.database.repository.CategoryRepository
import com.neoutils.finance.database.repository.TransactionRepository
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val repositoryModule = module {

    single<Settings> { Settings() }

    single<PreferencesRepository> { PreferencesRepositoryImpl(get()) }

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