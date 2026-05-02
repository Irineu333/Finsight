package com.neoutils.finsight.feature.recurring.di

import com.neoutils.finsight.feature.recurring.mapper.IRecurringMapper
import com.neoutils.finsight.feature.recurring.mapper.RecurringMapper
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringModalEntryImpl
import com.neoutils.finsight.feature.recurring.modal.recurringForm.RecurringFormModalEntry
import com.neoutils.finsight.feature.recurring.modal.recurringForm.RecurringFormModalEntryImpl
import com.neoutils.finsight.feature.recurring.modal.viewRecurring.ViewRecurringModalEntry
import com.neoutils.finsight.feature.recurring.modal.viewRecurring.ViewRecurringModalEntryImpl
import com.neoutils.finsight.feature.recurring.mapper.RecurringOccurrenceMapper
import com.neoutils.finsight.feature.recurring.repository.RecurringOccurrenceRepository
import com.neoutils.finsight.feature.recurring.repository.RecurringRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.recurring.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.IGetPendingRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.ReactivateRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.SaveRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.SkipRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.StopRecurringUseCase
import com.neoutils.finsight.feature.recurring.modal.confirmRecurring.ConfirmRecurringViewModel
import com.neoutils.finsight.feature.recurring.modal.deleteRecurring.DeleteRecurringViewModel
import com.neoutils.finsight.feature.recurring.modal.reactivateRecurring.ReactivateRecurringViewModel
import com.neoutils.finsight.feature.recurring.modal.recurringForm.RecurringFormViewModel
import com.neoutils.finsight.feature.recurring.modal.stopRecurring.StopRecurringViewModel
import com.neoutils.finsight.feature.recurring.screen.RecurringViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val recurringModule = module {

    single<ViewRecurringModalEntry> { ViewRecurringModalEntryImpl() }
    single<RecurringFormModalEntry> { RecurringFormModalEntryImpl() }
    single<ConfirmRecurringModalEntry> { ConfirmRecurringModalEntryImpl() }

    factory<IRecurringMapper> { RecurringMapper() }
    factory { RecurringMapper() }

    factory { RecurringOccurrenceMapper() }

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

    factory { SaveRecurringUseCase(repository = get()) }

    factory { ReactivateRecurringUseCase(repository = get()) }

    factory { StopRecurringUseCase(repository = get()) }

    factory<IGetPendingRecurringUseCase> { GetPendingRecurringUseCase() }
    factory { GetPendingRecurringUseCase() }

    factory {
        ConfirmRecurringUseCase(
            operationRepository = get(),
            recurringOccurrenceRepository = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    factory { SkipRecurringUseCase(recurringOccurrenceRepository = get()) }

    viewModel {
        RecurringViewModel(recurringRepository = get())
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
}
