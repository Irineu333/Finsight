@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.di

import com.neoutils.finance.extension.toYearMonth
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.modal.accountForm.AccountFormViewModel
import com.neoutils.finance.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finance.ui.modal.deleteAccount.DeleteAccountViewModel
import com.neoutils.finance.ui.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finance.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finance.ui.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finance.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finance.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finance.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finance.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finance.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finance.ui.modal.editAccountBalance.EditAccountBalanceViewModel
import com.neoutils.finance.ui.modal.editInvoiceBalance.EditInvoiceBalanceViewModel
import com.neoutils.finance.ui.modal.editInvoicePayment.EditInvoicePaymentViewModel
import com.neoutils.finance.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finance.ui.modal.payInvoice.PayInvoiceViewModel
import com.neoutils.finance.ui.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finance.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finance.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finance.ui.modal.viewTransaction.ViewOperationViewModel
import com.neoutils.finance.ui.screen.accounts.AccountsViewModel
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
            operation = it.get(),
            operationRepository = get(),
        )
    }

    viewModel {
        ViewOperationViewModel(
            operation = it.get(),
            operationRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            operationRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            ensureDefaultAccountUseCase = get(),
            invoiceUiMapper = get()
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

    viewModel { CategoriesViewModel(categoryRepository = get()) }

    viewModel {
        AccountsViewModel(
            accountRepository = get(),
            operationRepository = get(),
            categoryRepository = get(),
            initialAccountId = it.getOrNull(),
        )
    }

    viewModel {
        CreditCardsViewModel(
            initialCreditCardId = it.getOrNull(),
            creditCardRepository = get(),
            operationRepository = get(),
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
            operationRepository = get(),
            accountRepository = get(),
            buildTransactionUseCase = get(),
            addInstallmentUseCase = get(),
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
            accountRepository = get(),
            buildTransactionUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        DeleteTransactionViewModel(
            transaction = it.get(),
            operationRepository = get(),
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
        AccountFormViewModel(
            account = it.getOrNull(),
            validateAccountName = get(),
            createAccountUseCase = get(),
            updateAccountUseCase = get(),
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
        DeleteAccountViewModel(
            account = it.get(),
            deleteAccountUseCase = get(),
            modalManager = get()
        )
    }

    viewModel {
        EditAccountBalanceViewModel(
            type = it.get(),
            account = it.get(),
            targetMonth = it.getOrNull() ?: Clock.System.now().toYearMonth(),
            adjustBalanceUseCase = get(),
            adjustFinalBalanceUseCase = get(),
            adjustInitialBalanceUseCase = get(),
            calculateBalanceUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        EditInvoiceBalanceViewModel(
            initialInvoice = it.get(),
            adjustInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            invoiceRepository = get(),
            creditCardRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        PayInvoiceViewModel(
            invoiceId = it.get(),
            payInvoicePaymentUseCase = get(),
            payInvoiceUseCase = get(),
            calculateInvoiceUseCase = get(),
            accountRepository = get(),
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
            accountRepository = get(),
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
            operationRepository = get(),
            categoryRepository = get(),
        )
    }
}
