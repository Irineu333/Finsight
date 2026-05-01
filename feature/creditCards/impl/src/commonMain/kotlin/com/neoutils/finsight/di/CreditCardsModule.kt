package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.CreditCardMapper
import com.neoutils.finsight.database.mapper.InvoiceMapper
import com.neoutils.finsight.database.repository.CreditCardRepository
import com.neoutils.finsight.database.repository.InvoiceRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateRetroactiveInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.IGetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finsight.util.CreditCardPeriod
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val creditCardsModule = module {

    single { CreditCardMapper() }
    single { InvoiceMapper() }

    single<ICreditCardRepository> {
        CreditCardRepository(
            dao = get(),
            mapper = get(),
        )
    }

    single<IInvoiceRepository> {
        InvoiceRepository(
            dao = get(),
            mapper = get(),
            creditCardRepository = get(),
        )
    }

    factory {
        ValidateCreditCardNameUseCase(
            repository = get(),
        )
    }

    factory {
        OpenInvoiceUseCase(
            invoiceRepository = get(),
            creditCardRepository = get(),
        )
    }

    factory {
        AddCreditCardUseCase(
            repository = get(),
            openInvoiceUseCase = get(),
            validateCreditCardName = get(),
        )
    }

    factory {
        UpdateCreditCardUseCase(
            repository = get(),
            validateCreditCardName = get(),
        )
    }

    factory {
        PayInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        ReopenInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        CreateInvoiceUseCase(
            invoiceRepository = get(),
            creditCardRepository = get(),
        )
    }

    factory {
        CreateFutureInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        CreateRetroactiveInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory<IGetOrCreateInvoiceForMonthUseCase> {
        GetOrCreateInvoiceForMonthUseCase(
            invoiceRepository = get(),
            createFutureInvoiceUseCase = get(),
            createRetroactiveInvoiceUseCase = get(),
        )
    }

    factory {
        GetOrCreateInvoiceForMonthUseCase(
            invoiceRepository = get(),
            createFutureInvoiceUseCase = get(),
            createRetroactiveInvoiceUseCase = get(),
        )
    }

    single { CreditCardPeriod(defaultDaysDifference = 8) }

    factory { CalculateInvoiceUseCase(repository = get()) }

    factory { CalculateInvoiceOverviewsUseCase() }

    factory {
        CalculateAvailableLimitUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory {
        CloseInvoiceUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
            openInvoiceUseCase = get(),
        )
    }

    factory {
        PayInvoicePaymentUseCase(
            operationRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    factory {
        AdvanceInvoicePaymentUseCase(
            operationRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory {
        AdjustInvoiceUseCase(
            repository = get(),
            operationRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory {
        DeleteCreditCardUseCase(
            creditCardRepository = get(),
            operationRepository = get(),
        )
    }

    factory {
        DeleteFutureInvoiceUseCase(
            invoiceRepository = get(),
            operationRepository = get(),
        )
    }

    factory {
        InvoiceUiMapper(
            calculateInvoiceUseCase = get(),
            calculateAvailableLimitUseCase = get(),
        )
    }

    viewModel {
        CreditCardFormViewModel(
            formatter = get(),
            creditCard = it.getOrNull(),
            addCreditCardUseCase = get(),
            updateCreditCardUseCase = get(),
            validateCreditCardName = get(),
            modalManager = get(),
            debounceManager = get(),
            creditCardPeriod = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        DeleteCreditCardViewModel(
            creditCard = it.get(),
            deleteCreditCardUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        DeleteFutureInvoiceViewModel(
            invoice = it.get(),
            deleteFutureInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        CloseInvoiceViewModel(
            invoiceId = it.get(),
            closeInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        ReopenInvoiceViewModel(
            invoiceId = it.get(),
            reopenInvoiceUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
