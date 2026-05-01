package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory {
        AdjustInvoiceUseCase(
            repository = get(),
            operationRepository = get(),
            calculateInvoiceUseCase = get(),
        )
    }

    factory { CalculateInvoiceUseCase(repository = get()) }

    factory { CalculateCategorySpendingUseCase() }

    factory { CalculateCategoryIncomeUseCase() }

    factory { CalculateInvoiceOverviewsUseCase() }

    factory {
        CalculateAvailableLimitUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
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
        CloseInvoiceUseCase(
            invoiceRepository = get(),
            calculateInvoiceUseCase = get(),
            payInvoiceUseCase = get(),
            openInvoiceUseCase = get(),
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
}
