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
import com.neoutils.finsight.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateRetroactiveInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCaseImpl
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCaseImpl
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
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
        )
    }

    factory {
        UpdateCreditCardUseCase(
            repository = get(),
            validateCreditCardName = get(),
        )
    }

    factory {
        PayInvoicePaymentUseCase(
            transactionRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    factory {
        AdvanceInvoicePaymentUseCase(
            transactionRepository = get(),
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
        PayInvoiceUseCase(
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
        ReopenInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        OpenInvoiceUseCase(
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

    factory {
        CreateRetroactiveInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory<GetOrCreateInvoiceForMonthUseCase> {
        GetOrCreateInvoiceForMonthUseCaseImpl(
            invoiceRepository = get(),
            createFutureInvoiceUseCase = get(),
            createRetroactiveInvoiceUseCase = get(),
        )
    }






}
