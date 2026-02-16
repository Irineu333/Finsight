package com.neoutils.finance.di

import com.neoutils.finance.domain.usecase.CreateAccountUseCase
import com.neoutils.finance.domain.usecase.DeleteAccountUseCase
import com.neoutils.finance.domain.usecase.SetDefaultAccountUseCase
import com.neoutils.finance.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finance.domain.usecase.UpdateAccountUseCase
import com.neoutils.finance.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finance.domain.usecase.ValidateCategoryNameUseCase
import com.neoutils.finance.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.BuildTransactionUseCase
import com.neoutils.finance.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceOverviewsUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finance.domain.usecase.CloseInvoiceUseCase
import com.neoutils.finance.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finance.domain.usecase.CreateInvoiceUseCase
import com.neoutils.finance.domain.usecase.CreateRetroactiveInvoiceUseCase
import com.neoutils.finance.domain.usecase.DeleteFutureInvoiceUseCase
import com.neoutils.finance.domain.usecase.AddInstallmentUseCase
import com.neoutils.finance.domain.usecase.EnsureDefaultAccountUseCase
import com.neoutils.finance.domain.usecase.GetOrCreateInvoiceForMonthUseCase
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
