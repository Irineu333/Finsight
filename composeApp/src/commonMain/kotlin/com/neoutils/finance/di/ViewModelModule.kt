@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.di

import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.modal.addCategory.AddCategoryViewModel
import com.neoutils.finance.ui.modal.addCreditCard.AddCreditCardViewModel
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finance.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finance.ui.modal.editBalance.EditBalanceViewModel
import com.neoutils.finance.ui.modal.editCategory.EditCategoryViewModel
import com.neoutils.finance.ui.modal.editCreditCardLimit.EditCreditCardLimitViewModel
import com.neoutils.finance.ui.modal.editCreditCardName.EditCreditCardNameViewModel
import com.neoutils.finance.ui.modal.editInvoicePayment.EditInvoicePaymentViewModel
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finance.ui.modal.payBill.PayBillViewModel
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finance.ui.modal.viewCreditCard.ViewCreditCardViewModel
import com.neoutils.finance.ui.modal.viewTransaction.ViewTransactionViewModel
import com.neoutils.finance.ui.screen.categories.CategoriesViewModel
import com.neoutils.finance.ui.screen.creditCards.CreditCardsViewModel
import com.neoutils.finance.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finance.ui.screen.transactions.TransactionsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

val viewModelModule = module {
    viewModel {
        ViewCategoryViewModel(
            category = it.get(),
            categoryRepository = get(),
            repository = get()
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
            calculateCreditCardBillUseCase = get(),
            getOrCreateCurrentInvoiceUseCase = get(),
            creditCardBillUiMapper = get()
        )
    }

    viewModel {
        TransactionsViewModel(
            transaction = getOrNull(),
            category = getOrNull(),
            target = getOrNull(),
            transactionRepository = get(),
            categoryRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
        )
    }

    viewModel {
        CategoriesViewModel(
            getCategoriesUseCase = get()
        )
    }

    viewModel {
        CreditCardsViewModel(
            creditCardRepository = get(),
            transactionRepository = get(),
            calculateCreditCardBillUseCase = get(),
            getOrCreateCurrentInvoiceUseCase = get(),
            creditCardBillUiMapper = get()
        )
    }

    viewModel {
        AddTransactionViewModel(
            transactionRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            getOrCreateCurrentInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditTransactionViewModel(
            transactionRepository = get(),
            categoryRepository = get(),
            creditCardRepository = get(),
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
            getCategoriesUseCase = get(),
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
            initialBillAmount = it.get(),
            creditCardRepository = get(),
            transactionRepository = get(),
            calculateCreditCardBillUseCase = get(),
            getOrCreateCurrentInvoiceUseCase = get(),
            closeInvoiceUseCase = get(),
            payInvoiceUseCase = get()
        )
    }

    viewModel {
        DeleteCreditCardViewModel(
            creditCard = it.get(),
            deleteCreditCardUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditCategoryViewModel(
            category = it.get(),
            repository = get(),
            getCategoriesUseCase = get(),
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
            adjustCreditCardBillUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        PayBillViewModel(
            invoiceId = it.get(),
            payBillUseCase = get(),
            payInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditCreditCardLimitViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            transactionRepository = get(),
            calculateCreditCardBillUseCase = get(),
            getOrCreateCurrentInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditCreditCardNameViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            modalManager = get()
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
        com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceViewModel(
            invoiceId = it.get(),
            closeInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentViewModel(
            invoiceId = it.get(),
            payCreditCardBillUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceViewModel(
            invoiceId = it.get(),
            reopenInvoiceUseCase = get(),
            modalManager = get()
        )
    }
}