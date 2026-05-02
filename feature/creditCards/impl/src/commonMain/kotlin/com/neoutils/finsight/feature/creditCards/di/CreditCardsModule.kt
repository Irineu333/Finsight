package com.neoutils.finsight.feature.creditCards.di

import com.neoutils.finsight.feature.creditCards.mapper.CreditCardMapper
import com.neoutils.finsight.feature.creditCards.mapper.InvoiceMapper
import com.neoutils.finsight.feature.creditCards.repository.CreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.InvoiceRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.creditCards.usecase.AddCreditCardUseCase
import com.neoutils.finsight.feature.creditCards.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CreateRetroactiveInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.feature.creditCards.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.feature.creditCards.usecase.IGetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.feature.creditCards.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.feature.creditCards.usecase.PayInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.feature.creditCards.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.feature.creditCards.mapper.IInvoiceUiMapper
import com.neoutils.finsight.feature.creditCards.mapper.InvoiceUiMapper
import com.neoutils.finsight.feature.creditCards.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormModalEntryImpl
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.AdvancePaymentModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.AdvancePaymentModalEntryImpl
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.CloseInvoiceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.CloseInvoiceModalEntryImpl
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.EditInvoiceBalanceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.EditInvoiceBalanceModalEntryImpl
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.PayInvoiceModalEntry
import com.neoutils.finsight.feature.creditCards.modal.creditCardOps.PayInvoiceModalEntryImpl
import com.neoutils.finsight.feature.creditCards.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finsight.feature.creditCards.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finsight.feature.creditCards.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finsight.feature.creditCards.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finsight.feature.creditCards.modal.editInvoiceBalance.EditInvoiceBalanceViewModel
import com.neoutils.finsight.feature.creditCards.modal.payInvoice.PayInvoiceViewModel
import com.neoutils.finsight.feature.creditCards.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finsight.feature.creditCards.screen.CreditCardsViewModel
import com.neoutils.finsight.feature.creditCards.screen.invoiceTransactions.InvoiceTransactionsViewModel
import com.neoutils.finsight.feature.creditCards.util.CreditCardPeriod
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val creditCardsModule = module {

    single<CreditCardFormModalEntry> { CreditCardFormModalEntryImpl() }
    single<CloseInvoiceModalEntry> { CloseInvoiceModalEntryImpl() }
    single<PayInvoiceModalEntry> { PayInvoiceModalEntryImpl() }
    single<AdvancePaymentModalEntry> { AdvancePaymentModalEntryImpl() }
    single<EditInvoiceBalanceModalEntry> { EditInvoiceBalanceModalEntryImpl() }

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

    factory<IInvoiceUiMapper> {
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

    viewModel {
        EditInvoiceBalanceViewModel(
            initialInvoice = it.get(),
            adjustInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            invoiceRepository = get(),
            creditCardRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        PayInvoiceViewModel(
            invoiceId = it.get(),
            payInvoicePaymentUseCase = get(),
            payInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        AdvancePaymentViewModel(
            invoiceId = it.get(),
            advanceInvoicePaymentUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        CreditCardsViewModel(
            initialCreditCardId = it.getOrNull(),
            creditCardRepository = get(),
            operationRepository = get(),
            invoiceRepository = get(),
            invoiceUiMapper = get(),
            categoryRepository = get(),
        )
    }

    viewModel {
        InvoiceTransactionsViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            operationRepository = get(),
            categoryRepository = get(),
        )
    }
}
