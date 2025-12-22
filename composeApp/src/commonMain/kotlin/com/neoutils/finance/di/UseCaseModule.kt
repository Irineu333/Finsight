package com.neoutils.finance.di

import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finance.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import com.neoutils.finance.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory {
        AdjustBalanceUseCase(
            repository = get(),
            calculateBalanceUseCase = get(),
        )
    }

    factory {
        AdjustInvoiceUseCase(
            repository = get(),
            calculateInvoiceUseCase = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
        )
    }

    factory {
        AdjustFinalBalanceUseCase(
            adjustBalanceUseCase = get(),
        )
    }

    factory {
        AdjustInitialBalanceUseCase(
            adjustBalanceUseCase = get(),
        )
    }

    factory {
        CalculateBalanceUseCase(
            repository = get(),
        )
    }

    factory { CalculateInvoiceUseCase(repository = get()) }

    factory { CalculateTransactionStatsUseCase() }

    factory { CalculateCategorySpendingUseCase() }

    factory {
        AddCreditCardUseCase(
            repository = get(),
            openInvoiceUseCase = get(),
        )
    }

    factory {
        UpdateCreditCardUseCase(
            repository = get(),
        )
    }

    factory {
        PayInvoicePaymentUseCase(
            repository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    factory {
        AdvanceInvoicePaymentUseCase(
            repository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory {
        CloseInvoiceUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
        )
    }

    factory {
        PayInvoiceUseCase(
            invoiceRepository = get(),
            createInvoiceUseCase = get(),
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
}
