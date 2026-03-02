@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.di

import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.ui.modal.accountForm.AccountFormViewModel
import com.neoutils.finsight.ui.modal.categoryForm.CategoryFormViewModel
import com.neoutils.finsight.ui.modal.deleteAccount.DeleteAccountViewModel
import com.neoutils.finsight.ui.modal.creditCardForm.CreditCardFormViewModel
import com.neoutils.finsight.ui.modal.addInstallment.AddInstallmentViewModel
import com.neoutils.finsight.ui.modal.addTransaction.AddTransactionViewModel
import com.neoutils.finsight.ui.modal.advancePayment.AdvancePaymentViewModel
import com.neoutils.finsight.ui.modal.closeInvoice.CloseInvoiceViewModel
import com.neoutils.finsight.ui.modal.deleteCategory.DeleteCategoryViewModel
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.ui.modal.deleteCreditCard.DeleteCreditCardViewModel
import com.neoutils.finsight.ui.modal.deleteFutureInvoice.DeleteFutureInvoiceViewModel
import com.neoutils.finsight.ui.modal.deleteTransaction.DeleteTransactionViewModel
import com.neoutils.finsight.ui.modal.deleteInstallment.DeleteInstallmentViewModel
import com.neoutils.finsight.ui.modal.editAccountBalance.EditAccountBalanceViewModel
import com.neoutils.finsight.ui.modal.editInvoiceBalance.EditInvoiceBalanceViewModel
import com.neoutils.finsight.ui.modal.editTransaction.EditTransactionViewModel
import com.neoutils.finsight.ui.modal.payInvoice.PayInvoiceViewModel
import com.neoutils.finsight.ui.modal.reopenInvoice.ReopenInvoiceViewModel
import com.neoutils.finsight.ui.modal.transferBetweenAccounts.TransferBetweenAccountsViewModel
import com.neoutils.finsight.ui.modal.viewAdjustment.ViewAdjustmentViewModel
import com.neoutils.finsight.ui.modal.viewCategory.ViewCategoryViewModel
import com.neoutils.finsight.ui.modal.viewTransaction.ViewOperationViewModel
import com.neoutils.finsight.ui.screen.accounts.AccountsViewModel
import com.neoutils.finsight.ui.screen.categories.CategoriesViewModel
import com.neoutils.finsight.ui.screen.creditCards.CreditCardsViewModel
import com.neoutils.finsight.ui.screen.dashboard.DashboardViewModel
import com.neoutils.finsight.ui.modal.budgetForm.BudgetFormViewModel
import com.neoutils.finsight.ui.modal.deleteBudget.DeleteBudgetViewModel
import com.neoutils.finsight.ui.screen.budgets.BudgetsViewModel
import com.neoutils.finsight.ui.modal.confirmRecurring.ConfirmRecurringViewModel
import com.neoutils.finsight.ui.modal.deleteRecurring.DeleteRecurringViewModel
import com.neoutils.finsight.ui.modal.stopRecurring.StopRecurringViewModel
import com.neoutils.finsight.ui.modal.reactivateRecurring.ReactivateRecurringViewModel
import com.neoutils.finsight.ui.modal.recurringForm.RecurringFormViewModel
import com.neoutils.finsight.ui.screen.installments.InstallmentsViewModel
import com.neoutils.finsight.ui.screen.recurring.RecurringViewModel
import com.neoutils.finsight.ui.screen.reports.ReportsViewModel
import com.neoutils.finsight.ui.screen.transactions.TransactionsViewModel
import com.neoutils.finsight.util.CreditCardPeriod
import com.neoutils.finsight.util.DebounceManager
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
            recurringRepository = get(),
        )
    }

    viewModel {
        DashboardViewModel(
            operationRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            accountRepository = get(),
            budgetRepository = get(),
            recurringRepository = get(),
            recurringOccurrenceRepository = get(),
            calculateBalanceUseCase = get(),
            calculateTransactionStatsUseCase = get(),
            calculateCategorySpendingUseCase = get(),
            calculateBudgetProgressUseCase = get(),
            ensureDefaultAccountUseCase = get(),
            getPendingRecurringUseCase = get(),
            invoiceUiMapper = get()
        )
    }

    viewModel {
        BudgetsViewModel(
            budgetRepository = get(),
            operationRepository = get(),
            calculateBudgetProgressUseCase = get(),
        )
    }

    viewModel {
        BudgetFormViewModel(
            formatter = get(),
            budget = it.getOrNull(),
            budgetRepository = get(),
            categoryRepository = get(),
            validateBudgetTitle = get(),
            modalManager = get(),
            debounceManager = get(),
        )
    }

    viewModel {
        DeleteBudgetViewModel(
            budget = it.get(),
            budgetRepository = get(),
            modalManager = get(),
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

    viewModel {
        CategoriesViewModel(
            categoryRepository = get(),
            createDefaultCategories = get()
        )
    }

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
        InstallmentsViewModel(
            installmentRepository = get(),
            operationRepository = get(),
            installmentUiMapper = get(),
        )
    }

    viewModel {
        ReportsViewModel(
            accountRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            generateReportDocumentUseCase = get(),
        )
    }

    viewModel {
        AddInstallmentViewModel(
            categoryRepository = get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            addInstallmentUseCase = get(),
            modalManager = get(),
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
            operationRepository = get(),
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
        DeleteInstallmentViewModel(
            installment = it.get(),
            operations = it.get(),
            operationRepository = get(),
            installmentRepository = get(),
            modalManager = get(),
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
            initialType = it.getOrNull(),
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
            formatter = get(),
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
            deleteCreditCardUseCase = get(),
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
        TransferBetweenAccountsViewModel(
            initialSourceAccount = it.get(),
            transferBetweenAccountsUseCase = get(),
            accountRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        com.neoutils.finsight.ui.screen.invoiceTransactions.InvoiceTransactionsViewModel(
            creditCardId = it.get(),
            creditCardRepository = get(),
            invoiceRepository = get(),
            operationRepository = get(),
            categoryRepository = get(),
        )
    }

    viewModel {
        RecurringViewModel(
            recurringRepository = get(),
        )
    }

    viewModel {
        RecurringFormViewModel(
            recurring = it.getOrNull(),
            categoryRepository = get(),
            accountRepository = get(),
            creditCardRepository = get(),
            saveRecurringUseCase = get(),
            modalManager = get(),
        )
    }

    viewModel {
        DeleteRecurringViewModel(
            recurring = it.get(),
            recurringRepository = get(),
            modalManager = get(),
        )
    }

    viewModel {
        StopRecurringViewModel(
            recurring = it.get(),
            stopRecurringUseCase = get(),
            modalManager = get(),
        )
    }

    viewModel {
        ReactivateRecurringViewModel(
            recurring = it.get(),
            reactivateRecurringUseCase = get(),
            modalManager = get(),
        )
    }

    viewModel {
        ConfirmRecurringViewModel(
            recurring = it.get(),
            targetDate = it.get(),
            invoiceRepository = get(),
            confirmRecurringUseCase = get(),
            skipRecurringUseCase = get(),
            modalManager = get(),
        )
    }
}
