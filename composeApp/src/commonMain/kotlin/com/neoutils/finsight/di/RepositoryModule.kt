package com.neoutils.finsight.di

import com.neoutils.finsight.database.repository.BudgetRepository
import com.neoutils.finsight.database.repository.DashboardPreferencesRepository
import com.neoutils.finsight.database.repository.InstallmentRepository
import com.neoutils.finsight.database.repository.OperationRepository
import com.neoutils.finsight.database.repository.RecurringRepository
import com.neoutils.finsight.database.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IDashboardPreferencesRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.russhwolf.settings.Settings
import org.koin.dsl.module

val repositoryModule = module {

    single<Settings> { Settings() }

    single<IInstallmentRepository> {
        InstallmentRepository(
            installmentDao = get(),
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
