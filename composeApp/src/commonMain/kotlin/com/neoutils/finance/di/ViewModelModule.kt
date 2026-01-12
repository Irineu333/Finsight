@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.di

import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finance.ui.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finance.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finance.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finance.ui.modal.editBalance.EditBalanceViewModel
import com.neoutils.finance.ui.modal.editCreditCardLimit.EditCreditCardLimitViewModel
import com.neoutils.finance.ui.modal.editInvoicePayment.EditInvoicePaymentViewModel
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionViewModel
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
import com.neoutils.finance.util.CreditCardPeriod
import com.neoutils.finance.util.DebounceManager
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

val viewModelModule = module {

    single { ModalManager() }

    factory { DebounceManager(delayMillis = 500L) }

    factory { CreditCardPeriod(defaultDaysDifference = 8) }

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
            transactionRepository = get(),
            createFutureInvoiceUseCase = get(),
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
            createFutureInvoiceUseCase = get(),
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
        DeleteFutureInvoiceViewModel(
            invoice = it.get(),
            deleteFutureInvoiceUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        CategoryFormViewModel(
            category = it.getOrNull(),
            repository = get(),
            validateCategoryName = get(),
            modalManager = get(),
            debounceManager = get()
        )
    }

    viewModel {
        CreditCardFormViewModel(
            creditCard = it.getOrNull(),
            addCreditCardUseCase = get(),
            updateCreditCardUseCase = get(),
            validateCreditCardName = get(),
            modalManager = get(),
            debounceManager = get(),
            creditCardPeriod = get(),
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
        com.neoutils.finance.ui.screen.invoiceTransactions.InvoiceTransactionsViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            transactionRepository = get(),
            categoryRepository = get(),
        )
    }
}
