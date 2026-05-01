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
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.extension.signedImpact
import com.neoutils.finsight.currentPlatform
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
)

data class DashboardBuilderContext(
    val allTransactions: List<Transaction>,
    val pendingRecurring: List<Recurring>,
)

class DashboardComponentsBuilder(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateTransactionStatsUseCase: CalculateTransactionStatsUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateCategoryIncomeUseCase: CalculateCategoryIncomeUseCase,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val getPendingRecurringUseCase: GetPendingRecurringUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
) {

    suspend fun build(
        key: String,
        input: DashboardComponentsInput,
        context: DashboardBuilderContext,
        config: Map<String, String>,
    ): DashboardComponent? {
        return when (key) {
            DashboardComponentType.TOTAL_BALANCE.key -> totalBalance(input, context.allTransactions)
            DashboardComponentType.CONCRETE_BALANCE_STATS.key -> concreteBalanceStats(input, config)
            DashboardComponentType.PENDING_BALANCE_STATS.key -> pendingBalanceStats(
                pendingRecurring = context.pendingRecurring,
                config = config,
            )

            DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key -> creditCardBalanceStats(
                input = input,
                allTransactions = context.allTransactions,
                config = config,
            )

            DashboardComponentType.ACCOUNTS_OVERVIEW.key -> accountsOverview(
                input = input,
                allTransactions = context.allTransactions,
                config = config
            )

            DashboardComponentType.CREDIT_CARDS_PAGER.key -> creditCardsPager(input, config)
            DashboardComponentType.SPENDING_BY_CATEGORY.key -> spendingByCategory(
                input = input,
                allTransactions = context.allTransactions,
                config = config
            )

            DashboardComponentType.INCOME_BY_CATEGORY.key -> incomeByCategory(
                input = input,
                allTransactions = context.allTransactions,
                config = config
            )

            DashboardComponentType.BUDGETS.key -> budgets(input, context.allTransactions)
            DashboardComponentType.PENDING_RECURRING.key -> pendingRecurring(
                pendingRecurring = context.pendingRecurring,
                input = input,
                config = config,
            )

            DashboardComponentType.RECENTS.key -> recents(input, config)
            DashboardComponentType.QUICK_ACTIONS.key -> quickActions(config)
            else -> null
        }
    }

    fun createContext(input: DashboardComponentsInput): DashboardBuilderContext {
        return DashboardBuilderContext(
            allTransactions = input.operations.flatMap { it.transactions },
            pendingRecurring = getPendingRecurringUseCase(
                recurringList = input.recurringList,
                occurrences = input.occurrences,
                today = input.today,
            )
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

    private fun creditCardBalanceStats(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
        config: Map<String, String>,
    ): DashboardComponent.CreditCardBalanceStats? {
        val monthTransactions = allTransactions.filter { it.date.yearMonth == input.targetMonth }

        val payment = monthTransactions
            .filter { it.target == Transaction.Target.CREDIT_CARD }
            .filter { it.type == Transaction.Type.INCOME }
            .filter { it.isInvoicePayment }
            .sumOf { it.amount }

        val expense = monthTransactions
            .filter { it.target == Transaction.Target.CREDIT_CARD }
            .filter { it.type == Transaction.Type.EXPENSE }
            .sumOf { it.amount }

        val isEmpty = payment <= 0.0 && expense <= 0.0

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
            DashboardComponent.CreditCardBalanceStats(
                payment = payment,
                expense = expense,
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

    private fun spendingByCategory(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
        config: Map<String, String>,
    ): DashboardComponent.SpendingByCategory? {
        val maxCategories = config[SpendingByCategoryConfig.MAX_CATEGORIES]
            ?.toIntOrNull() ?: SpendingByCategoryConfig.ALL.toInt()

        val categorySpending = calculateCategorySpendingUseCase(
            transactions = allTransactions,
            forYearMonth = input.targetMonth,
        ).let { if (maxCategories >= 0) it.take(maxCategories) else it }

        return if (categorySpending.isNotEmpty()) {
            DashboardComponent.SpendingByCategory(
                categorySpending = categorySpending,
            )
        } else {
            null
        }
    }

    private fun incomeByCategory(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
        config: Map<String, String>,
    ): DashboardComponent.IncomeByCategory? {
        val maxCategories = config[IncomeByCategoryConfig.MAX_CATEGORIES]
            ?.toIntOrNull() ?: IncomeByCategoryConfig.ALL.toInt()

        val categoryIncome = calculateCategoryIncomeUseCase(
            transactions = allTransactions,
            forYearMonth = input.targetMonth,
        ).let { if (maxCategories >= 0) it.take(maxCategories) else it }

        return if (categoryIncome.isNotEmpty()) {
            DashboardComponent.IncomeByCategory(
                categoryIncome = categoryIncome,
            )
        } else {
            null
        }
    }

    private fun budgets(
        input: DashboardComponentsInput,
        allTransactions: List<Transaction>,
    ): DashboardComponent.Budgets? {
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = input.budgets,
            transactions = allTransactions,
            recurringList = input.recurringList,
            operations = input.operations,
        )

        return if (budgetProgress.isNotEmpty()) {
            DashboardComponent.Budgets(
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
        val daysAhead = config[PendingRecurringConfig.UPCOMING_DAYS_AHEAD]
            ?.toIntOrNull() ?: PendingRecurringConfig.DEFAULT_UPCOMING_DAYS_AHEAD
        val currentYearMonth = input.today.yearMonth
        val pendingIds = pendingRecurring.map { it.id }.toSet()
        val handledRecurringIds = input.occurrences
            .asSequence()
            .filter { it.yearMonth == currentYearMonth }
            .map { it.recurringId }
            .toSet()

        val upcomingRecurring = input.recurringList.filter { recurring ->
            val effectiveDay = currentYearMonth.effectiveDay(recurring.dayOfMonth)

            recurring.isActive &&
                recurring.id !in handledRecurringIds &&
                recurring.id !in pendingIds &&
                effectiveDay > input.today.day &&
                effectiveDay - input.today.day <= daysAhead
        }

        val visibleRecurring = (pendingRecurring + upcomingRecurring)
            .sortedWith(
                compareBy<Recurring> { currentYearMonth.effectiveDay(it.dayOfMonth) }
                    .thenBy { it.createdAt }
            )

        return if (visibleRecurring.isNotEmpty()) {
            DashboardComponent.PendingRecurring(recurringList = visibleRecurring)
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
            QuickActionType.SUPPORT.takeUnless { currentPlatform.isDesktop },
        )

        return DashboardComponent.QuickActions(actions = allActions.filter { it.name !in hiddenActions })
    }
}
