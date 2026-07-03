package com.neoutils.finsight.di

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.repository.CreditCardRepository
import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.database.repository.InvoiceRepository
import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.extension.CurrencyFormatter
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val repositoryModule = module {

    single<Settings> { Settings() }

    single { CurrencyFormatter() }

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

    single<IInstallmentRepository> {
        InstallmentRepository(
            installmentDao = get(),
        )
    }


    single<IDashboardPreferencesRepository> {
        DashboardPreferencesRepository(
            settings = get(),
        )
    }
}
