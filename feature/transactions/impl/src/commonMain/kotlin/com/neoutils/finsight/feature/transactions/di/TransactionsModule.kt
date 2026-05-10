package com.neoutils.finsight.feature.transactions.di

import com.neoutils.finsight.feature.transactions.mapper.IOperationUiMapper
import com.neoutils.finsight.feature.transactions.mapper.OperationMapper
import com.neoutils.finsight.feature.transactions.mapper.OperationUiMapper
import com.neoutils.finsight.feature.transactions.mapper.TransactionMapper
import com.neoutils.finsight.feature.transactions.repository.OperationRepository
import com.neoutils.finsight.feature.transactions.repository.TransactionRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import com.neoutils.finsight.feature.transactions.usecase.BuildTransactionUseCase
import com.neoutils.finsight.feature.transactions.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.feature.transactions.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.feature.transactions.usecase.ICalculateTransactionStatsUseCase
import com.neoutils.finsight.feature.transactions.usecase.IBuildTransactionUseCase
import com.neoutils.finsight.feature.transactions.usecase.ICalculateBalanceUseCase
import com.neoutils.finsight.feature.transactions.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finsight.feature.transactions.screen.TransactionsEntry
import com.neoutils.finsight.feature.transactions.screen.TransactionsViewModel
import com.neoutils.finsight.feature.transactions.entryPoint.TransactionsEntryImpl
import com.neoutils.finsight.feature.transactions.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finsight.feature.transactions.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finsight.feature.transactions.modal.ViewAdjustmentModalEntry
import com.neoutils.finsight.feature.transactions.entryPoint.ViewAdjustmentModalEntryImpl
import com.neoutils.finsight.feature.transactions.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finsight.feature.transactions.modal.ViewOperationModalEntry
import com.neoutils.finsight.feature.transactions.entryPoint.ViewOperationModalEntryImpl
import com.neoutils.finsight.feature.transactions.modal.viewTransaction.ViewOperationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val transactionsModule = module {

    single<TransactionsEntry> { TransactionsEntryImpl() }
    single<ViewOperationModalEntry> { ViewOperationModalEntryImpl() }
    single<ViewAdjustmentModalEntry> { ViewAdjustmentModalEntryImpl() }

    single { TransactionMapper() }
    single { OperationMapper() }
    factory<IOperationUiMapper> {
        OperationUiMapper(
            accountRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            invoiceUiMapper = get(),
        )
    }

    single<ITransactionRepository> {
        TransactionRepository(
            dao = get(),
            mapper = get(),
        )
    }

    single<IOperationRepository> {
        OperationRepository(
            operationDao = get(),
            transactionDao = get(),
            recurringDao = get(),
            installmentRepository = get(),
            operationMapper = get(),
            recurringMapper = get(),
            transactionMapper = get(),
        )
    }

    factory<IBuildTransactionUseCase> {
        BuildTransactionUseCase(
            getOrCreateInvoiceForMonth = get(),
        )
    }

    factory<ICalculateBalanceUseCase> {
        CalculateBalanceUseCase(repository = get())
    }

    factory {
        CalculateBalanceUseCase(repository = get())
    }

    factory<ICalculateTransactionStatsUseCase> { CalculateTransactionStatsUseCase() }
    factory { CalculateTransactionStatsUseCase() }

    viewModel {
        EditTransactionViewModel(
            transaction = it.get(),
            transactionRepository = get(),
            operationRepository = get(),
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
            operationRepository = get(),
            modalManager = get(),
            analytics = get(),
        )
    }

    viewModel {
        ViewAdjustmentViewModel(
            operation = it.get(),
            operationRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
        )
    }

    viewModel {
        ViewOperationViewModel(
            operationId = it.get(),
            perspective = it.get(),
            operationRepository = get(),
            accountRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        AddTransactionViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            operationRepository = get(),
            accountRepository = get(),
            buildTransactionUseCase = get(),
            addInstallmentUseCase = get(),
            modalManager = get(),
            analytics = get(),
            crashlytics = get(),
        )
    }

    viewModel {
        TransactionsViewModel(
            filterType = getOrNull(),
            category = getOrNull(),
            filterTarget = getOrNull(),
            operationRepository = get(),
            categoryRepository = get(),
            operationUiMapper = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }
}
