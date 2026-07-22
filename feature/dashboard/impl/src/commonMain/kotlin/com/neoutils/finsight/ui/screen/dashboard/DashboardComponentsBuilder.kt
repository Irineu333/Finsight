package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.extension.deriveTransactionLabel
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.yearMonth

data class DashboardComponentsInput(
    val transactions: List<Transaction>,
    val creditCards: List<CreditCard>,
    val invoicesByCreditCardId: Map<Long, Invoice>,
    val accounts: List<Account>,
    val budgets: List<Budget>,
    val recurringList: List<Recurring>,
    val occurrences: List<RecurringOccurrence>,
    val today: LocalDate,
    val targetMonth: YearMonth,
    val facadeLookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
)

data class DashboardBuilderContext(
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
    private val entryRepository: IEntryRepository,
    private val navCatalog: NavCatalog,
) {

    suspend fun build(
        key: String,
        input: DashboardComponentsInput,
        context: DashboardBuilderContext,
        config: Map<String, String>,
    ): DashboardComponent? {
        return when (key) {
            DashboardComponentType.TOTAL_BALANCE.key -> totalBalance(input)
            DashboardComponentType.CONCRETE_BALANCE_STATS.key -> concreteBalanceStats(input, config)
            DashboardComponentType.PENDING_BALANCE_STATS.key -> pendingBalanceStats(
                pendingRecurring = context.pendingRecurring,
                config = config,
            )

            DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key -> creditCardBalanceStats(
                input = input,
                config = config,
            )

            DashboardComponentType.ACCOUNTS_OVERVIEW.key -> accountsOverview(
                input = input,
                config = config
            )

            DashboardComponentType.CREDIT_CARDS_PAGER.key -> creditCardsPager(input, config)
            DashboardComponentType.SPENDING_BY_CATEGORY.key -> spendingByCategory(
                input = input,
                config = config
            )

            DashboardComponentType.INCOME_BY_CATEGORY.key -> incomeByCategory(
                input = input,
                config = config
            )

            DashboardComponentType.BUDGETS.key -> budgets(input)
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
            pendingRecurring = getPendingRecurringUseCase(
                recurringList = input.recurringList,
                occurrences = input.occurrences,
                today = input.today,
            )
        )
    }

    private suspend fun totalBalance(
        input: DashboardComponentsInput,
    ): DashboardComponent.TotalBalance {
        // Σ entries of all ASSET accounts up to the target month, from the ledger (task 4.3).
        return DashboardComponent.TotalBalance(
            amount = calculateBalanceUseCase(target = input.targetMonth),
        )
    }

    private fun concreteBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.ConcreteBalanceStats? {
        // Transfers and card payments move money between the user's own accounts:
        // they are not income or expense. Derived from the ledger, never persisted.
        val transactionsForStats = input.transactions.filterNot { transaction ->
            transaction.entries.deriveTransactionLabel() in setOf(TransactionLabel.TRANSFER, TransactionLabel.PAYMENT)
        }

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

    private suspend fun creditCardBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.CreditCardBalanceStats? {
        // Month-wide card expense/payment from the ledger (task 4.11).
        val flows = entryRepository.liabilityMonthFlows(input.targetMonth)
        val payment = flows.payment
        val expense = flows.expense

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

    private suspend fun accountsOverview(
        input: DashboardComponentsInput,
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
                // All-time natural balance from the ledger (task 4.5), replacing the
                // in-builder per-account sum that used to live here.
                DashboardAccountUi(
                    id = account.id,
                    iconKey = account.iconKey,
                    name = account.name,
                    isDefault = account.isDefault,
                    balance = entryRepository.balance(account.id),
                )
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
                val ui = CreditCardUi(
                    cardId = creditCard.id,
                    iconKey = creditCard.iconKey,
                    name = creditCard.name,
                    closingDay = creditCard.closingDay,
                    dueDay = creditCard.dueDay,
                    limit = creditCard.limit,
                    // The dashboard shows a summary and offers no reopen action, so it
                    // has no need of the sibling list `canReopen` would derive from.
                    invoiceUi = invoice?.let {
                        invoiceUiMapper.toUi(invoice = it, cardInvoices = listOfNotNull(it))
                    },
                )
                ui to invoice
            }

        return when {
            creditCardsWithBills.isNotEmpty() -> DashboardComponent.CreditCardsPager.Content(
                creditCards = creditCardsWithBills.map { it.first },
                domainInvoices = creditCardsWithBills.map { it.second },
            )

            showEmptyState -> DashboardComponent.CreditCardsPager.Empty
            else -> null
        }
    }

    private suspend fun spendingByCategory(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.SpendingByCategory? {
        val maxCategories = config[SpendingByCategoryConfig.MAX_CATEGORIES]
            ?.toIntOrNull() ?: SpendingByCategoryConfig.ALL.toInt()

        val categorySpending = calculateCategorySpendingUseCase(
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

    private suspend fun incomeByCategory(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.IncomeByCategory? {
        val maxCategories = config[IncomeByCategoryConfig.MAX_CATEGORIES]
            ?.toIntOrNull() ?: IncomeByCategoryConfig.ALL.toInt()

        val categoryIncome = calculateCategoryIncomeUseCase(
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

    private suspend fun budgets(
        input: DashboardComponentsInput,
    ): DashboardComponent.Budgets? {
        val budgetProgress = calculateBudgetProgressUseCase(
            budgets = input.budgets,
            recurringList = input.recurringList,
            transactions = input.transactions,
            month = input.targetMonth,
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
        val presentTransactions = input.transactions.filter { it.date <= input.today }
        val recentTransactions = presentTransactions
            .sortedByDescending { it.date }
            .take(count)

        return if (recentTransactions.isNotEmpty()) {
            DashboardComponent.Recents(
                transactions = recentTransactions,
                hasMore = presentTransactions.size > count,
                facadeLookup = input.facadeLookup,
            )
        } else {
            null
        }
    }

    private fun quickActions(config: Map<String, String>): DashboardComponent.QuickActions? {
        // On desktop the persistent rail already exposes every feature, so the quick-actions grid is
        // redundant — omit the whole section (rather than render an empty, header-only gap).
        if (isDesktop) return null

        val hiddenActions = parseHiddenActionKeys(config)

        val allActions = navCatalog.destinations.filter { !it.primaryTab }

        return DashboardComponent.QuickActions(actions = allActions.filter { it.actionKey !in hiddenActions })
    }
}
