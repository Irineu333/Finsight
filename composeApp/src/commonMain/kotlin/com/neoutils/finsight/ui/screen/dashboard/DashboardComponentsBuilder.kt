package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.signedImpact
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

data class DashboardComponentsInput(
    val operations: List<Operation>,
    val creditCards: List<CreditCard>,
    val invoicesByCreditCardId: Map<Long, Invoice>,
    val accounts: List<Account>,
    val budgets: List<Budget>,
    val recurringList: List<Recurring>,
    val occurrences: List<RecurringOccurrence>,
    val today: LocalDate,
    val targetMonth: YearMonth,
)

class DashboardComponentsBuilder(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val getPendingRecurringUseCase: GetPendingRecurringUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
) {

    suspend fun build(input: DashboardComponentsInput): List<DashboardComponent> {
        val allTransactions = input.operations.flatMap { it.transactions }
        val pendingRecurring = getPendingRecurringUseCase(
            recurringList = input.recurringList,
            occurrences = input.occurrences,
            today = input.today,
        )
        return listOfNotNull(
            totalBalance(input, allTransactions),
            concreteBalanceStats(input),
            pendingBalanceStats(pendingRecurring),
            accountsOverview(input, allTransactions),
            creditCardsPager(input),
            spendingPager(input, allTransactions),
            pendingRecurring(pendingRecurring),
            recents(input),
            quickActions(),
        )
    }

    private fun totalBalance(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
    ): DashboardComponent.TotalBalance {
        return DashboardComponent.TotalBalance(
            amount = calculateBalanceUseCase(
                target = input.targetMonth,
                transactions = allTransactions,
            ),
        )
    }

    private fun concreteBalanceStats(input: DashboardComponentsInput): DashboardComponent.ConcreteBalanceStats {
        val transactionsForStats = input.operations
            .filterNot { it.kind == Operation.Kind.TRANSFER || it.kind == Operation.Kind.PAYMENT }
            .flatMap { it.transactions }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactionsForStats,
            forYearMonth = input.targetMonth,
        )

        return DashboardComponent.ConcreteBalanceStats(
            income = stats.income,
            expense = stats.expense,
        )
    }

    private fun pendingBalanceStats(pendingRecurring: List<Recurring>): DashboardComponent.PendingBalanceStats? {
        val pendingIncome = pendingRecurring.filter { it.type.isIncome }.sumOf { it.amount }
        val pendingExpense = pendingRecurring.filter { it.type.isExpense }.sumOf { it.amount }

        return if (pendingIncome > 0.0 || pendingExpense > 0.0) {
            DashboardComponent.PendingBalanceStats(
                pendingIncome = pendingIncome,
                pendingExpense = pendingExpense,
            )
        } else {
            null
        }
    }

    private fun accountsOverview(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
    ): DashboardComponent.AccountsOverview? {
        val accountsUi = input.accounts.map { account ->
            val accountTransactions = allTransactions.filter { it.account?.id == account.id }
            val balance = accountTransactions.sumOf { it.signedImpact() }
            DashboardAccountUi(
                account = account,
                balance = balance,
            )
        }

        return if (accountsUi.size > 1) {
            DashboardComponent.AccountsOverview(
                accounts = accountsUi,
            )
        } else {
            null
        }
    }

    private suspend fun creditCardsPager(input: DashboardComponentsInput): DashboardComponent.CreditCardsPager? {
        if (input.creditCards.isEmpty()) return null

        val creditCardsWithBills = input.creditCards.map { creditCard ->
            val invoice = input.invoicesByCreditCardId[creditCard.id]

            CreditCardUi(
                creditCard = creditCard,
                invoiceUi = invoice?.let { invoiceUiMapper.toUi(invoice = it) },
            )
        }

        return if (creditCardsWithBills.isNotEmpty()) {
            DashboardComponent.CreditCardsPager(
                creditCards = creditCardsWithBills,
            )
        } else {
            null
        }
    }

    private fun spendingPager(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
    ): DashboardComponent.SpendingPager? {
        val categorySpending = calculateCategorySpendingUseCase(
            transactions = allTransactions,
            forYearMonth = input.targetMonth,
        )
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = input.budgets,
            transactions = allTransactions,
            recurringList = input.recurringList,
            operations = input.operations,
        )

        return if (categorySpending.isNotEmpty() || budgetProgress.isNotEmpty()) {
            DashboardComponent.SpendingPager(
                categorySpending = categorySpending,
                budgetProgress = budgetProgress,
            )
        } else {
            null
        }
    }

    private fun pendingRecurring(pendingRecurring: List<Recurring>): DashboardComponent.PendingRecurring? {
        return if (pendingRecurring.isNotEmpty()) {
            DashboardComponent.PendingRecurring(
                recurringList = pendingRecurring,
            )
        } else {
            null
        }
    }

    private fun recents(input: DashboardComponentsInput): DashboardComponent.Recents? {
        val presentOperations = input.operations.filter { it.date <= input.today }
        val recentOperations = presentOperations
            .sortedByDescending { it.date }
            .take(4)

        return if (recentOperations.isNotEmpty()) {
            DashboardComponent.Recents(
                operations = recentOperations,
                hasMore = presentOperations.size > 3,
            )
        } else {
            null
        }
    }

    private fun quickActions(): DashboardComponent.QuickActions {
        return DashboardComponent.QuickActions(
            actions = listOfNotNull(
                QuickActionType.BUDGETS,
                QuickActionType.CATEGORIES,
                QuickActionType.CREDIT_CARDS,
                QuickActionType.ACCOUNTS,
                QuickActionType.RECURRING,
                QuickActionType.REPORTS,
                QuickActionType.INSTALLMENTS,
                QuickActionType.SUPPORT.takeUnless { isDesktop },
            ),
        )
    }
}
