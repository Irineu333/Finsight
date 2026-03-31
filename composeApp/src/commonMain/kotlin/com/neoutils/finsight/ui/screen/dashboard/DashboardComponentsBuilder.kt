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
import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.extension.signedImpact
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

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
    val configByKey: Map<String, Map<String, String>> = emptyMap(),
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

        fun configFor(key: String) = input.configByKey[key] ?: emptyMap()

        return listOfNotNull(
            totalBalance(input, allTransactions),
            concreteBalanceStats(input, configFor(DashboardComponent.ConcreteBalanceStats.KEY)),
            pendingBalanceStats(
                pendingRecurring = pendingRecurring,
                config = configFor(DashboardComponent.PendingBalanceStats.KEY),
            ),
            accountsOverview(input, allTransactions, configFor(DashboardComponent.AccountsOverview.KEY)),
            creditCardsPager(input, configFor(DashboardComponent.CreditCardsPager.KEY)),
            spendingPager(input, allTransactions, configFor(DashboardComponent.SpendingPager.KEY)),
            pendingRecurring(pendingRecurring, input, configFor(DashboardComponent.PendingRecurring.KEY)),
            recents(input, configFor(DashboardComponent.Recents.KEY)),
            quickActions(configFor(DashboardComponent.QuickActions.KEY)),
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

    private fun concreteBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.ConcreteBalanceStats? {
        val transactionsForStats = input.operations
            .filterNot { it.kind == Operation.Kind.TRANSFER || it.kind == Operation.Kind.PAYMENT }
            .flatMap { it.transactions }

        val stats = calculateTransactionStatsUseCase(
            transactions = transactionsForStats,
            forYearMonth = input.targetMonth,
        )

        val isEmpty = stats.income <= 0.0 && stats.expense <= 0.0
        if (isEmpty && config.hideWhenEmpty(defaultValue = false)) {
            return null
        }

        return DashboardComponent.ConcreteBalanceStats(
            income = stats.income,
            expense = stats.expense,
        )
    }

    private fun pendingBalanceStats(
        pendingRecurring: List<Recurring>,
        config: Map<String, String>,
    ): DashboardComponent.PendingBalanceStats? {
        val pendingIncome = pendingRecurring.filter { it.type.isIncome }.sumOf { it.amount }
        val pendingExpense = pendingRecurring.filter { it.type.isExpense }.sumOf { it.amount }
        val isEmpty = pendingIncome <= 0.0 && pendingExpense <= 0.0

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
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
        config: Map<String, String>,
    ): DashboardComponent.AccountsOverview? {
        val hideSingleAccount = config[AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT] != "false"
        val excludedIds = config[AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS]
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

        val accountsUi = input.accounts
            .filter { it.id !in excludedIds }
            .map { account ->
                val accountTransactions = allTransactions.filter { it.account?.id == account.id }
                val balance = accountTransactions.sumOf { it.signedImpact() }
                DashboardAccountUi(account = account, balance = balance)
            }

        return if (accountsUi.isNotEmpty() && !(hideSingleAccount && accountsUi.size == 1)) {
            DashboardComponent.AccountsOverview(accounts = accountsUi)
        } else {
            null
        }
    }

    private suspend fun creditCardsPager(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.CreditCardsPager? {
        val showEmptyState = config[DashboardComponentConfig.SHOW_EMPTY_STATE] == "true"
        if (input.creditCards.isEmpty() && !showEmptyState) return null

        val excludedIds = config[CreditCardsPagerConfig.EXCLUDED_CARD_IDS]
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet() ?: emptySet()

        val creditCardsWithBills = input.creditCards
            .filter { it.id !in excludedIds }
            .map { creditCard ->
                val invoice = input.invoicesByCreditCardId[creditCard.id]
                CreditCardUi(
                    creditCard = creditCard,
                    invoiceUi = invoice?.let { invoiceUiMapper.toUi(invoice = it) },
                )
            }

        return when {
            creditCardsWithBills.isNotEmpty() -> DashboardComponent.CreditCardsPager.Content(
                creditCards = creditCardsWithBills,
            )

            showEmptyState -> DashboardComponent.CreditCardsPager.Empty
            else -> null
        }
    }

    private fun spendingPager(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
        config: Map<String, String>,
    ): DashboardComponent.SpendingPager? {
        val maxCategories = config[SpendingPagerConfig.MAX_CATEGORIES]
            ?.toIntOrNull() ?: SpendingPagerConfig.ALL.toInt()

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = allTransactions,
            forYearMonth = input.targetMonth,
        ).let { if (maxCategories >= 0) it.take(maxCategories) else it }

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

    private fun pendingRecurring(
        pendingRecurring: List<Recurring>,
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.PendingRecurring? {
        val daysAhead = config[PendingRecurringConfig.DAYS_AHEAD]
            ?.toIntOrNull() ?: PendingRecurringConfig.DEFAULT_DAYS_AHEAD

        val filtered = pendingRecurring.filter { recurring ->
            val effectiveDay = input.today.yearMonth.effectiveDay(recurring.dayOfMonth)
            input.today.day - effectiveDay <= daysAhead
        }

        return if (filtered.isNotEmpty()) {
            DashboardComponent.PendingRecurring(recurringList = filtered)
        } else {
            null
        }
    }

    private fun recents(input: DashboardComponentsInput, config: Map<String, String>): DashboardComponent.Recents? {
        val count = config[RecentsConfig.COUNT]?.toIntOrNull() ?: RecentsConfig.DEFAULT_COUNT
        val presentOperations = input.operations.filter { it.date <= input.today }
        val recentOperations = presentOperations
            .sortedByDescending { it.date }
            .take(count)

        return if (recentOperations.isNotEmpty()) {
            DashboardComponent.Recents(
                operations = recentOperations,
                hasMore = presentOperations.size > count,
            )
        } else {
            null
        }
    }

    private fun quickActions(config: Map<String, String>): DashboardComponent.QuickActions {
        val hiddenActions = config[QuickActionsConfig.HIDDEN_ACTIONS]
            ?.split(",")
            ?.filter { it.isNotEmpty() }
            ?.toSet() ?: emptySet()

        val allActions = listOfNotNull(
            QuickActionType.BUDGETS,
            QuickActionType.CATEGORIES,
            QuickActionType.CREDIT_CARDS,
            QuickActionType.ACCOUNTS,
            QuickActionType.RECURRING,
            QuickActionType.REPORTS,
            QuickActionType.INSTALLMENTS,
            QuickActionType.SUPPORT.takeUnless { isDesktop },
        )

        return DashboardComponent.QuickActions(actions = allActions.filter { it.name !in hiddenActions })
    }
}
