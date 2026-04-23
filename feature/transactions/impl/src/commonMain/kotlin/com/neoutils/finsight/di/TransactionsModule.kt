package com.neoutils.finsight.di

import com.neoutils.finsight.database.mapper.TransactionMapper
import com.neoutils.finsight.database.repository.TransactionRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.BuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.IBuildTransactionUseCase
import com.neoutils.finsight.domain.usecase.ICalculateBalanceUseCase
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val transactionsModule = module {

    single { TransactionMapper() }

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
}
