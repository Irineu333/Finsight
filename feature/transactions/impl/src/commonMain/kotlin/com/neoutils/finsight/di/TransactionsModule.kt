package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.OperationMapper
import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.OperationRepository
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.IBuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.ICalculateBalanceUseCase
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finsight.ui.screen.transactions.TransactionsEntry
import com.neoutils.finsight.ui.screen.transactions.TransactionsViewModel
import com.neoutils.finsight.ui.screen.transactions.entry.TransactionsEntryImpl
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModalEntry
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentModalEntryImpl
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModalEntry
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationModalEntryImpl
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val transactionsModule = module {

    single<TransactionsEntry> { TransactionsEntryImpl() }
    single<ViewOperationModalEntry> { ViewOperationModalEntryImpl() }
    single<ViewAdjustmentModalEntry> { ViewAdjustmentModalEntryImpl() }

    single { TransactionMapper() }
    single { OperationMapper() }

    single<ITransactionRepository> {
        TransactionRepository(
            dao = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            mapper = get(),
        )
    }

    single<IOperationRepository> {
        OperationRepository(
            operationDao = get(),
            transactionDao = get(),
            recurringDao = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            installmentRepository = get(),
            accountRepository = get(),
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
        )
    }

    viewModel {
        ViewOperationViewModel(
            operation = it.get(),
            perspective = it.getOrNull(),
            operationRepository = get(),
            recurringRepository = get(),
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
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }
}
