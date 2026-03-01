package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CreateRetroactiveInvoiceUseCase
import com.neoutils.finsight.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finsight.domain.usecase.AddInstallmentUseCase
import com.neoutils.finsight.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finsight.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finsight.domain.usecase.OpenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.domain.usecase.ReopenInvoiceUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
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

    factory { CalculateBudgetProgressUseCase() }

    factory { CalculateInvoiceOverviewsUseCase() }

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
        ValidateCategoryNameUseCase(
            repository = get(),
        )
    }

    factory {
        ValidateCreditCardNameUseCase(
            repository = get(),
        )
    }

    factory {
        ValidateAccountNameUseCase(
            repository = get(),
        )
    }

    factory {
        SetDefaultAccountUseCase(
            repository = get(),
        )
    }

    factory {
        CreateAccountUseCase(
            repository = get(),
            validateAccountName = get(),
            setDefaultAccount = get(),
        )
    }

    factory {
        UpdateAccountUseCase(
            repository = get(),
            validateAccountName = get(),
            setDefaultAccount = get(),
        )
    }

    factory {
        DeleteAccountUseCase(
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
        CreateRetroactiveInvoiceUseCase(
            invoiceRepository = get(),
        )
    }

    factory {
        GetOrCreateInvoiceForMonthUseCase(
            invoiceRepository = get(),
            createFutureInvoiceUseCase = get(),
            createRetroactiveInvoiceUseCase = get(),
        )
    }

    factory {
        BuildTransactionUseCase(
            getOrCreateInvoiceForMonthUseCase = get(),
        )
    }

    factory {
        CreateDefaultCategoriesUseCase(
            categoryRepository = get(),
        )
    }

    factory {
        EnsureDefaultAccountUseCase(
            repository = get(),
        )
    }

    factory {
        TransferBetweenAccountsUseCase(
            operationRepository = get(),
            accountRepository = get(),
        )
    }
}
