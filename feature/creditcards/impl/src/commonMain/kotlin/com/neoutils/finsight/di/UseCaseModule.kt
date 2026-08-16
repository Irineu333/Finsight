package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCaseImpl
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCaseImpl
import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCaseImpl
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCaseImpl
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCaseImpl
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateInstallmentUseCase
import com.neoutils.finsight.domain.usecase.UpdateInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory<AdjustInvoiceUseCase> {
        AdjustInvoiceUseCaseImpl(
            invoiceRepository = get(),
            transactionRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory<CalculateInvoiceUseCase> { CalculateInvoiceUseCaseImpl(entryRepository = get()) }

    factory { CalculateInvoiceOverviewsUseCase(entryRepository = get()) }

    factory<CalculateAvailableLimitUseCase> {
        CalculateAvailableLimitUseCaseImpl(
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory<AddCreditCardUseCase> {
        AddCreditCardUseCaseImpl(
            repository = get(),
            openInvoiceUseCase = get(),
            validateCreditCardName = get(),
            clock = get(),
        )
    }

    factory<UpdateCreditCardUseCase> {
        UpdateCreditCardUseCaseImpl(
            repository = get(),
            validateCreditCardName = get(),
        )
    }

    factory<PayInvoicePaymentUseCase> {
        PayInvoicePaymentUseCaseImpl(
            harvestExchangeRate = get(),
            accountRepository = get(),
            transactionRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    factory<AdvanceInvoicePaymentUseCase> {
        AdvanceInvoicePaymentUseCaseImpl(
            harvestExchangeRate = get(),
            accountRepository = get(),
            transactionRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            clock = get(),
        )
    }

    factory<CloseInvoiceUseCase> {
        CloseInvoiceUseCaseImpl(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
            openInvoiceUseCase = get(),
        )
    }

    factory<PayInvoiceUseCase> {
        PayInvoiceUseCaseImpl(
            invoiceRepository = get(),
            clock = get(),
        )
    }

    factory<CreateInvoiceUseCase> {
        CreateInvoiceUseCaseImpl(
            creditCardRepository = get(),
            invoiceRepository = get(),
        )
    }

    factory<ReopenInvoiceUseCase> {
        ReopenInvoiceUseCaseImpl(
            invoiceRepository = get(),
        )
    }

    factory<OpenInvoiceUseCase> {
        OpenInvoiceUseCaseImpl(
            invoiceRepository = get(),
            creditCardRepository = get(),
            clock = get(),
        )
    }

    factory {
        ValidateCreditCardNameUseCase(
            repository = get(),
        )
    }

    factory<DeleteInstallmentUseCase> {
        DeleteInstallmentUseCaseImpl(
            transactionRepository = get(),
            installmentRepository = get(),
            installmentDao = get(),
        )
    }

    factory<UpdateInstallmentUseCase> {
        UpdateInstallmentUseCaseImpl(
            installmentRepository = get(),
        )
    }

    factory<DeleteCreditCardUseCase> {
        DeleteCreditCardUseCaseImpl(
            creditCardRepository = get(),
            entryRepository = get(),
            recurringRepository = get(),
        )
    }

    factory<ArchiveCreditCardUseCase> {
        ArchiveCreditCardUseCaseImpl(
            creditCardRepository = get(),
            accountRepository = get(),
            archiveAccountUseCase = get(),
        )
    }

    factory<UnarchiveCreditCardUseCase> { UnarchiveCreditCardUseCaseImpl(repository = get()) }

    factory<DeleteFutureInvoiceUseCase> {
        DeleteFutureInvoiceUseCaseImpl(
            invoiceRepository = get(),
            transactionRepository = get(),
        )
    }

    factory<AddInstallmentUseCase> {
        AddInstallmentUseCaseImpl(
            transactionRepository = get(),
            installmentRepository = get(),
            invoiceRepository = get(),
            buildTransactionUseCase = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    factory<GetOrCreateInvoiceForMonthUseCase> {
        GetOrCreateInvoiceForMonthUseCaseImpl(
            creditCardRepository = get(),
            invoiceRepository = get(),
            createInvoiceUseCase = get(),
        )
    }
}
