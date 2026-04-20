package com.neoutils.finsight.di

import com.neoutils.finsight.database.AppDatabase
import com.neoutils.finsight.database.repository.BudgetRepository
import com.neoutils.finsight.database.repository.CategoryRepository
import com.neoutils.finsight.database.repository.CreditCardRepository
import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.database.repository.InvoiceRepository
import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.database.repository.OperationRepository
import com.neoutils.finsight.database.repository.RecurringRepository
import com.neoutils.finsight.database.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
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
            dao = get(),
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

    single<ITransactionRepository> {
        TransactionRepository(
            dao = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            mapper = get(),
        )
    }

    single<IBudgetRepository> {
        BudgetRepository(
            dao = get(),
            mapper = get(),
            categoryRepository = get(),
        )
    }

    single<IOperationRepository> {
        OperationRepository(
            operationDao = get(),
            transactionDao = get(),
            recurringDao = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            installmentRepository = get(),
            accountRepository = get(),
            operationMapper = get(),
            recurringMapper = get(),
            transactionMapper = get(),
        )
    }

    single<IRecurringRepository> {
        RecurringRepository(
            dao = get(),
            mapper = get(),
            categoryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
        )
    }

    single<IRecurringOccurrenceRepository> {
        RecurringOccurrenceRepository(
            dao = get(),
            mapper = get(),
        )
    }

    single<IDashboardPreferencesRepository> {
        DashboardPreferencesRepository(
            settings = get(),
        )
    }
}
