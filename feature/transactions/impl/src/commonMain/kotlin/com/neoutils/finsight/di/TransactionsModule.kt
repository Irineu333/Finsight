package com.neoutils.finsight.di

import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCase
import com.neoutils.finsight.domain.usecase.ValidateTransactionFormUseCaseImpl
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCase
import com.neoutils.finsight.domain.usecase.RegisterTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransactionUseCaseImpl
import com.neoutils.finsight.feature.transactions.api.TransactionsEntry
import com.neoutils.finsight.feature.transactions.impl.TransactionsEntryImpl
import com.neoutils.finsight.ui.model.LedgerTransactionFacadeResolver
import com.neoutils.finsight.ui.model.TransactionFacadeResolver
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finsight.ui.modal.viewTransaction.ViewTransactionViewModel
import com.neoutils.finsight.ui.screen.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val transactionsModule = module {
    factory<TransactionFacadeResolver> {
        LedgerTransactionFacadeResolver(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            installmentRepository = get(),
            recurringRepository = get(),
        )
    }
    factory<DeleteTransactionUseCase> { DeleteTransactionUseCaseImpl(transactionRepository = get()) }

    factory<ValidateTransactionFormUseCase> {
        ValidateTransactionFormUseCaseImpl(
            clock = get(),
        )
    }

    factory<BuildTransactionUseCase> {
        BuildTransactionUseCaseImpl(
            getOrCreateInvoiceForMonthUseCase = get(),
            validateTransactionForm = get(),
        )
    }

    factory<UpdateTransactionUseCase> {
        UpdateTransactionUseCaseImpl(
            transactionRepository = get(),
            buildTransaction = get(),
        )
    }

    factory<RegisterTransactionUseCase> {
        RegisterTransactionUseCaseImpl(
            transactionRepository = get(),
            buildTransaction = get(),
            addInstallment = get(),
            startRecurringFromTransaction = get(),
        )
    }

    single<TransactionsEntry> { TransactionsEntryImpl() }

    viewModel {
        ViewAdjustmentViewModel(
            transactionId = it.get(),
            transactionRepository = get(),
            facadeResolver = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        ViewTransactionViewModel(
            transactionId = it.get(),
            transactionRepository = get(),
            facadeResolver = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        TransactionsViewModel(
            filterLabel = getOrNull(),
            filterTarget = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            installmentRepository = get(),
            entryRepository = get(),
            consolidateMoney = get(),
            observeConsolidationChanges = get(),
            baseCurrencyRepository = get(),
            clock = get(),
        )
    }
    viewModel {
        AddTransactionViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            registerTransaction = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
            validateTransactionForm = get(),
            clock = get(),
        )
    }
    viewModel {
        EditTransactionViewModel(
            transaction = it.get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            updateTransaction = get(),
            validateTransactionForm = get(),
            formatter = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
            clock = get(),
        )
    }
    viewModel {
        DeleteTransactionViewModel(
            transaction = it.get(),
            categoryRepository = get(),
            deleteTransactionUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
}
