@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.di

import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.addCategory.AddCategoryViewModel
import com.neoutils.finance.ui.modal.addCreditCard.AddCreditCardViewModel
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finance.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finance.ui.modal.editBalance.EditBalanceViewModel
import com.neoutils.finance.ui.modal.editCategory.EditCategoryViewModel
import com.neoutils.finance.ui.modal.editCreditCard.EditCreditCardViewModel
import com.neoutils.finance.ui.modal.editCreditCardLimit.EditCreditCardLimitViewModel
import com.neoutils.finance.ui.modal.editInvoicePayment.EditInvoicePaymentViewModel
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finance.ui.modal.openInvoice.OpenInvoiceViewModel
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceViewModel
import com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finance.ui.modal.viewCreditCard.ViewCreditCardViewModel
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionViewModel
import com.neoutils.finance.ui.screen.categories.CategoriesViewModel
import com.neoutils.finance.ui.screen.creditCards.CreditCardsViewModel
import com.neoutils.finance.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finance.ui.screen.transactions.TransactionsViewModel
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    single { ModalManager() }

    viewModel {
        ViewCategoryViewModel(
            category = it.get(),
            categoryRepository = get(),
            transactionRepository = get()
        )
    }

    viewModel {
        ViewAdjustmentViewModel(
            transaction = it.get(),
            transactionRepository = get()
        )
    }

    viewModel {
        ViewTransactionViewModel(
            transaction = it.get(),
            transactionRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            transactionRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            invoiceUiMapper = get()
        )
    }

    viewModel {
        TransactionsViewModel(
            transaction = getOrNull(),
            category = getOrNull(),
            target = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            invoiceRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateInvoiceOverviewsUseCase = get(),
        )
    }

    viewModel { CategoriesViewModel(categoryRepository = get()) }

    viewModel {
        CreditCardsViewModel(
            creditCardRepository = get(),
            transactionRepository = get(),
            invoiceRepository = get(),
            invoiceUiMapper = get(),
            categoryRepository = get(),
        )
    }

    viewModel {
        AddTransactionViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            addTransactionUseCase = get(),
            invoiceUiMapper = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditTransactionViewModel(
            transaction = it.get(),
            transactionRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            invoiceUiMapper = get(),
            modalManager = get()
        )
    }

    viewModel {
        DeleteTransactionViewModel(
            transaction = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        AddCategoryViewModel(
            initialType = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        AddCreditCardViewModel(
            addCreditCardUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        ViewCreditCardViewModel(
            creditCard = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            transactionRepository = get(),
            invoiceUiMapper = get(),
        )
    }

    viewModel {
        DeleteCreditCardViewModel(
            creditCard = it.get(),
            creditCardRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        EditCategoryViewModel(
            category = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        DeleteCategoryViewModel(
            category = it.get(),
            repository = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditBalanceViewModel(
            type = it.get(),
            targetMonth = it.getOrNull() ?: Clock.System.now().toYearMonth(),
            invoiceId = it.getOrNull(),
            adjustBalanceUseCase = get(),
            adjustFinalBalanceUseCase = get(),
            adjustInitialBalanceUseCase = get(),
            adjustInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        PayInvoiceViewModel(
            invoiceId = it.get(),
            payInvoiceUseCase = get(),
            payInvoicePaymentUseCase = get(),
            calculateInvoiceUseCase = get(),
            modalManager = get(),
        )
    }

    viewModel {
        EditCreditCardLimitViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            updateCreditCardUseCase = get(),
            invoiceUiMapper = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditCreditCardViewModel(
            creditCardId = it.get(),
            updateCreditCardUseCase = get(),
            modalManager = get(),
        )
    }

    viewModel {
        EditInvoicePaymentViewModel(
            transaction = it.get(),
            transactionRepository = get(),
            modalManager = get()
        )
    }

    viewModel {
        CloseInvoiceViewModel(
            invoiceId = it.get(),
            closeInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        AdvancePaymentViewModel(
            invoiceId = it.get(),
            advanceInvoicePaymentUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        ReopenInvoiceViewModel(
            invoiceId = it.get(),
            reopenInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        OpenInvoiceViewModel(
            creditCardId = it.get(),
            openInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        com.neoutils.finance.ui.screen.invoiceTransactions.InvoiceTransactionsViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
        )
    }
}
