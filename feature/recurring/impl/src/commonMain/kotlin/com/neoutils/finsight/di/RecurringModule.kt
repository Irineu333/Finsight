package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCase
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.database.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.database.repository.RecurringRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.ResolveRecurringRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCase
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.feature.recurring.impl.RecurringEntryImpl
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringViewModel
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringViewModel
import com.neoutils.finsight.ui.modal.unarchiveRecurring.UnarchiveRecurringViewModel
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormViewModel
import com.neoutils.finsight.ui.modal.archiveRecurring.ArchiveRecurringViewModel
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringViewModel
import com.neoutils.finsight.ui.screen.recurring.RecurringViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val recurringModule = module {
    single<IRecurringRepository> {
        RecurringRepository(
            database = get(),
            dao = get(),
            mapper = get(),
            categoryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
        )
    }
    single<IRecurringOccurrenceRepository> {
        RecurringOccurrenceRepository(
            database = get(),
            dao = get(),
            mapper = get(),
            transactionRepository = get(),
        )
    }
    factory { RecurringMapper() }
    factory { RecurringOccurrenceMapper() }

    factory { GetPendingRecurringUseCase() }
    factory {
        ResolveRecurringRetirabilityUseCase(
            recurringRepository = get(),
            budgetRepository = get(),
        )
    }
    factory {
        DeleteRecurringUseCase(
            repository = get(),
            resolveRetirability = get(),
        )
    }
    factory { SaveRecurringUseCase(repository = get()) }
    factory { UnarchiveRecurringUseCase(repository = get()) }
    factory { ArchiveRecurringUseCase(repository = get()) }
    factory {
        ConfirmRecurringUseCase(
            accountRepository = get(),
            recurringOccurrenceRepository = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }
    factory { SkipRecurringUseCase(recurringOccurrenceRepository = get()) }

    single<RecurringEntry> { RecurringEntryImpl() }

    viewModel {
        RecurringViewModel(
            recurringRepository = get(),
            accountRepository = get(),
        )
    }
    viewModel {
        ViewRecurringViewModel(
            recurringId = it.get(),
            recurringRepository = get(),
            accountRepository = get(),
            resolveRetirability = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        RecurringFormViewModel(
            recurring = it.getOrNull(),
            categoryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            saveRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ConfirmRecurringViewModel(
            recurring = it.get(),
            targetDate = it.get(),
            accountRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            confirmRecurringUseCase = get(),
            skipRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
    clock = get(),
        )
    }
    viewModel {
        DeleteRecurringViewModel(
            recurring = it.get(),
            deleteRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ArchiveRecurringViewModel(
            recurring = it.get(),
            archiveRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        UnarchiveRecurringViewModel(
            recurring = it.get(),
            unarchiveRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
