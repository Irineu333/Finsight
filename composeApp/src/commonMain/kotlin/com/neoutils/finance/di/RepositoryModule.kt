package com.neoutils.finance.di

import com.neoutils.finance.database.AppDatabase
import com.neoutils.finance.database.repository.CategoryRepository
import com.neoutils.finance.database.repository.CreditCardRepository
import com.neoutils.finance.database.repository.InvoiceRepository
import com.neoutils.finance.database.repository.TransactionRepository
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val repositoryModule = module {

    single<Settings> { Settings() }

    single<ICategoryRepository> {
        CategoryRepository(
            dao = get(),
            mapper = get(),
        )
    }

    single<ICreditCardRepository> {
        CreditCardRepository(
            dao = get<AppDatabase>().creditCardDao(),
            mapper = get()
        )
    }

    single<IInvoiceRepository> {
        InvoiceRepository(
            dao = get(),
            mapper = get(),
            creditCardRepository = get(),
        )
    }

    single<ITransactionRepository> {
        TransactionRepository(
            dao = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            mapper = get(),
        )
    }
}