package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCase
import com.neoutils.finsight.domain.usecase.DeleteRecurringUseCaseImpl
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.database.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.database.repository.RecurringRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCaseImpl
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.ResolveRecurringRetirabilityUseCase
import com.neoutils.finsight.domain.usecase.ResolveRecurringRetirabilityUseCaseImpl
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveRecurringUseCaseImpl
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCaseImpl
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCaseImpl
import com.neoutils.finsight.domain.usecase.StartRecurringFromTransactionUseCase
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCase
import com.neoutils.finsight.domain.usecase.ArchiveRecurringUseCaseImpl
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.feature.recurring.impl.RecurringEntryImpl
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringViewModel
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringViewModel
import com.neoutils.finsight.ui.modal.skipRecurring.SkipRecurringViewModel
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
            occurrenceRepository = get(),
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
    factory<ResolveRecurringRetirabilityUseCase> {
        ResolveRecurringRetirabilityUseCaseImpl(
            recurringRepository = get(),
            budgetRepository = get(),
        )
    }
    factory<DeleteRecurringUseCase> {
        DeleteRecurringUseCaseImpl(
            repository = get(),
            resolveRetirability = get(),
        )
    }
    factory<SaveRecurringUseCase> { SaveRecurringUseCaseImpl(repository = get()) }
    factory {
        StartRecurringFromTransactionUseCase(
            repository = get(),
            clock = get(),
        )
    }
    factory<UnarchiveRecurringUseCase> { UnarchiveRecurringUseCaseImpl(repository = get()) }
    factory<ArchiveRecurringUseCase> { ArchiveRecurringUseCaseImpl(repository = get()) }
    factory<ConfirmRecurringUseCase> {
        ConfirmRecurringUseCaseImpl(
            recurringRepository = get(),
            accountRepository = get(),
            recurringOccurrenceRepository = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
            clock = get(),
        )
    }
    factory<SkipRecurringUseCase> {
        SkipRecurringUseCaseImpl(
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            clock = get(),
        )
    }

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
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
            clock = get(),
        )
    }
    viewModel {
        SkipRecurringViewModel(
            recurring = it.get(),
            date = it.get(),
            target = it.get(),
            skipRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
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
