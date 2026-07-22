package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.EntryRepository
import com.neoutils.finsight.database.repository.LedgerEntryWriter
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCase
import com.neoutils.finsight.domain.usecase.DeleteTransactionUseCaseImpl
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
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
    single<ITransactionRepository> {
        TransactionRepository(
            database = get(),
            transactionDao = get(),
            entryDao = get(),
            accountRepository = get(),
            writeGuard = get(),
            removalHook = get(),
            transactionMapper = get(),
            ledgerEntryWriter = get(),
        )
    }
    single<IEntryRepository> { EntryRepository(entryDao = get()) }
    factory {
        LedgerEntryWriter(
            entryDao = get(),
            accountDao = get(),
            dimensionDao = get(),
        )
    }
    factory { TransactionMapper() }
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

    factory { CalculateTransactionStatsUseCase() }
    factory { CalculateBalanceUseCase(entryRepository = get()) }
    factory<BuildTransactionUseCase> {
        BuildTransactionUseCaseImpl(
            getOrCreateInvoiceForMonthUseCase = get(),
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
            perspective = it.getOrNull(),
            transactionRepository = get(),
            facadeResolver = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        TransactionsViewModel(
            filterType = getOrNull(),
            category = getOrNull(),
            filterTarget = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            installmentRepository = get(),
            entryRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }
    viewModel {
        AddTransactionViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            transactionRepository = get(),
            accountRepository = get(),
            buildTransactionUseCase = get(),
            addInstallmentUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }
    viewModel {
        EditTransactionViewModel(
            transaction = it.get(),
            transactionRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            buildTransactionUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
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
