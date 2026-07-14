package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.RecurringMapper
import com.neoutils.finsight.database.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.database.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.database.repository.RecurringRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.ReactivateRecurringUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.domain.usecase.StopRecurringUseCase
import com.neoutils.finsight.feature.recurring.api.RecurringEntry
import com.neoutils.finsight.feature.recurring.impl.RecurringEntryImpl
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringViewModel
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringViewModel
import com.neoutils.finsight.ui.modal.reactivateRecurring.ReactivateRecurringViewModel
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormViewModel
import com.neoutils.finsight.ui.modal.stopRecurring.StopRecurringViewModel
import com.neoutils.finsight.ui.modal.viewRecurring.ViewRecurringViewModel
import com.neoutils.finsight.ui.screen.recurring.RecurringViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val recurringModule = module {
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
    factory { RecurringMapper() }
    factory { RecurringOccurrenceMapper() }

    factory { GetPendingRecurringUseCase() }
    factory { SaveRecurringUseCase(repository = get()) }
    factory { ReactivateRecurringUseCase(repository = get()) }
    factory { StopRecurringUseCase(repository = get()) }
    factory {
        ConfirmRecurringUseCase(
            operationRepository = get(),
            recurringOccurrenceRepository = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }
    factory { SkipRecurringUseCase(recurringOccurrenceRepository = get()) }

    single<RecurringEntry> { RecurringEntryImpl() }

    viewModel {
        RecurringViewModel(
            recurringRepository = get(),
        )
    }
    viewModel {
        ViewRecurringViewModel(
            recurringId = it.get(),
            recurringRepository = get(),
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
            creditCardRepository = get(),
            invoiceRepository = get(),
            confirmRecurringUseCase = get(),
            skipRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        DeleteRecurringViewModel(
            recurring = it.get(),
            recurringRepository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }
    viewModel {
        StopRecurringViewModel(
            recurring = it.get(),
            stopRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ReactivateRecurringViewModel(
            recurring = it.get(),
            reactivateRecurringUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
