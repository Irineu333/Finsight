package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.usecase.SaveRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.domain.usecase.StopRecurringUseCase
import com.neoutils.finsight.domain.usecase.ReactivateRecurringUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.ValidateBudgetTitleUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateReportStatsUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AddSupportReplyUseCase
import com.neoutils.finsight.domain.usecase.CreateSupportIssueUseCase
import org.koin.dsl.module

val useCaseModules = module {
    factory {
        AdjustBalanceUseCase(
            repository = get(),
            operationRepository = get(),
            calculateBalanceUseCase = get(),
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

    factory { CalculateCategoryIncomeUseCase() }

    factory { CalculateReportStatsUseCase() }

    factory { CalculateReportCategorySpendingUseCase() }

    factory { CalculateBudgetProgressUseCase() }

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
        ValidateBudgetTitleUseCase(
            repository = get(),
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
        AddInstallmentUseCase(
            operationRepository = get(),
            installmentRepository = get(),
            invoiceRepository = get(),
            buildTransactionUseCase = get(),
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    factory {
        BuildTransactionUseCase(
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    factory {
        TransferBetweenAccountsUseCase(
            operationRepository = get(),
            accountRepository = get(),
        )
    }

    factory { SaveRecurringUseCase(repository = get()) }

    factory { ReactivateRecurringUseCase(repository = get()) }

    factory { StopRecurringUseCase(repository = get()) }

    factory { GetPendingRecurringUseCase() }

    factory { ConfirmRecurringUseCase(operationRepository = get(), recurringOccurrenceRepository = get(), getOrCreateInvoiceForMonthUseCase = get()) }

    factory { SkipRecurringUseCase(recurringOccurrenceRepository = get()) }

    factory { CreateSupportIssueUseCase(supportRepository = get()) }

    factory { AddSupportReplyUseCase(supportRepository = get()) }
}
