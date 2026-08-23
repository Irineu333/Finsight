package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.ArchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCase
import com.neoutils.finsight.domain.usecase.DeleteInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCaseImpl
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateAdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ValidateInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.WriteInvoicePaymentUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory {
        AdjustInvoiceUseCase(
            transactionRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory { CalculateInvoiceUseCase(entryRepository = get()) }

    factory { CalculateInvoiceOverviewsUseCase(entryRepository = get()) }

    factory {
        CalculateAvailableLimitUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory {
        AddCreditCardUseCase(
            repository = get(),
            openInvoiceUseCase = get(),
            validateCreditCardName = get(),
            clock = get(),
        )
    }

    factory {
        UpdateCreditCardUseCase(
            repository = get(),
            validateCreditCardName = get(),
        )
    }

    // The shape an invoice payment takes in the ledger — one owner, both modes.
    factory {
        WriteInvoicePaymentUseCase(
            transactionRepository = get(),
            harvestExchangeRate = get(),
            accountRepository = get(),
        )
    }

    factory {
        PayInvoicePaymentUseCase(
            writeInvoicePayment = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    // Every rule a partial payment is admissible by — one owner, and both modes read
    // it, so registering one and correcting one cannot drift apart.
    factory {
        ValidateInvoicePaymentUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            clock = get(),
        )
    }

    factory {
        AdvanceInvoicePaymentUseCase(
            writeInvoicePayment = get(),
            validateInvoicePayment = get(),
        )
    }

    factory {
        UpdateAdvanceInvoicePaymentUseCase(
            writeInvoicePayment = get(),
            validateInvoicePayment = get(),
            transactionRepository = get(),
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
        PayInvoiceUseCase(
            invoiceRepository = get(),
            clock = get(),
        )
    }

    factory {
        CreateInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        ReopenInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        OpenInvoiceUseCase(
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
        )
    }

    factory {
        DeleteCreditCardUseCase(
            creditCardRepository = get(),
            entryRepository = get(),
            recurringRepository = get(),
        )
    }

    factory {
        ArchiveCreditCardUseCase(
            accountRepository = get(),
            archiveAccountUseCase = get(),
        )
    }

    factory { UnarchiveCreditCardUseCase(repository = get()) }

    factory {
        DeleteFutureInvoiceUseCase(
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
            invoiceRepository = get(),
            createInvoiceUseCase = get(),
        )
    }






}
