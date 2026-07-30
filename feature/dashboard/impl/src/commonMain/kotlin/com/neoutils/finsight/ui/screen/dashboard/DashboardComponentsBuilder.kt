package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.ConsolidateFigureUseCase
import com.neoutils.finsight.domain.usecase.consolidationDateOf
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.effectiveDay
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.isDesktop
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.BudgetProgressUi
import com.neoutils.finsight.ui.model.CategorySpendingUi
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
    /**
     * The currency every figure that spans accounts is reduced to. It travels with the input,
     * like [today] does, so one build has exactly one base — two widgets of the same screen
     * reading the preference a moment apart would render half a dashboard in each.
     */
    val baseCurrency: String,
    val facadeLookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
)

data class DashboardBuilderContext(
    val pendingRecurring: List<Recurring>,
)

class DashboardComponentsBuilder(
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateCategoryIncomeUseCase: CalculateCategoryIncomeUseCase,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val getPendingRecurringUseCase: GetPendingRecurringUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val entryRepository: IEntryRepository,
    private val consolidateFigure: ConsolidateFigureUseCase,
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
            DashboardComponentType.OVERALL_BALANCE_STATS.key -> overallBalanceStats(input, config)
            DashboardComponentType.CONCRETE_BALANCE_STATS.key -> concreteBalanceStats(input, config)
            DashboardComponentType.PENDING_BALANCE_STATS.key -> pendingBalanceStats(
                pendingRecurring = context.pendingRecurring,
                input = input,
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
        // The read spans every account, so it comes back per currency and the consolidation
        // layer is what reduces it to what the card renders.
        return DashboardComponent.TotalBalance(
            amount = input.figure(calculateBalanceUseCase(target = input.targetMonth)).figure,
        )
    }

    private suspend fun overallBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.OverallBalanceStats? {
        // The neutral perimeter (ASSET + LIABILITY) is the *sum* of the two per-nature
        // reads the ledger already exposes — `ledger-reporting` forbids a third aggregate
        // for it (design D2). The two expense sets are disjoint: a card purchase has no
        // ASSET leg, so nothing is double-counted; and neither read reports an invoice
        // payment, which is internal to this perimeter. Income only ever lands on ASSET.
        val asset = entryRepository.assetMonthFlows(input.targetMonth)
        val liability = entryRepository.liabilityMonthFlows(input.targetMonth)

        // The sum of two per-currency figures is the ledger's own operation — each currency
        // added to its own, nothing converted here.
        val income = asset.income
        val expense = asset.expense + liability.expense

        val isEmpty = income.movedNothing() && expense.movedNothing()
        if (isEmpty && config.hideWhenEmpty(defaultValue = false)) {
            return null
        }

        return DashboardComponent.OverallBalanceStats(
            income = input.figure(income).figure,
            expense = input.figure(expense).figure,
        )
    }

    private suspend fun concreteBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.ConcreteBalanceStats? {
        // Month-wide income/expense across the user's ASSET accounts, straight from the
        // ledger — transfers and card payments (money between the user's own accounts)
        // are excluded there, not re-derived here (spec `ledger-reporting`).
        val stats = entryRepository.assetMonthFlows(input.targetMonth)
        val isEmpty = stats.income.movedNothing() && stats.expense.movedNothing()
        if (isEmpty && config.hideWhenEmpty(defaultValue = false)) {
            return null
        }

        return DashboardComponent.ConcreteBalanceStats(
            income = input.figure(stats.income).figure,
            expense = input.figure(stats.expense).figure,
        )
    }

    private suspend fun pendingBalanceStats(
        pendingRecurring: List<Recurring>,
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.PendingBalanceStats? {
        // A recurring is denominated by the account or card it names, so a list of them is a
        // per-currency figure before it is one number — the same shape a ledger read has.
        val pendingIncome = pendingRecurring.filter { it.type.isIncome }.balance()
        val pendingExpense = pendingRecurring.filter { it.type.isExpense }.balance()
        val isEmpty = pendingIncome.movedNothing() && pendingExpense.movedNothing()

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
            DashboardComponent.PendingBalanceStats(
                pendingIncome = input.figure(pendingIncome).figure,
                pendingExpense = input.figure(pendingExpense).figure,
            )
        } else {
            null
        }
    }

    /**
     * Whether nothing at all moved. It is asked of the per-currency result and not of the
     * consolidated one, so a widget hides or shows for the same reason it always did — a
     * currency no rate reached still counts as movement.
     */
    private fun CurrencyBalance.movedNothing() = entries.values.none { it > 0.0 }

    /**
     * A list of recurrings as a per-currency figure. One whose account and card are both gone
     * denominates nothing and is left out: it cannot be posted either, so counting its number
     * would put an amount in the total in a currency nobody can name.
     */
    private fun List<Recurring>.balance(): CurrencyBalance = CurrencyBalance.of(
        mapNotNull { recurring -> recurring.currency?.let { it to recurring.amount } }
            .groupBy({ it.first }, { it.second })
            .mapValues { (_, amounts) -> amounts.sum() }
    )

    private suspend fun creditCardBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.CreditCardBalanceStats? {
        // Month-wide card expense/payment from the ledger (task 4.11).
        val flows = entryRepository.liabilityMonthFlows(input.targetMonth)
        val isEmpty = flows.payment.movedNothing() && flows.expense.movedNothing()

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
            DashboardComponent.CreditCardBalanceStats(
                payment = input.figure(flows.payment).figure,
                expense = input.figure(flows.expense).figure,
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
                    balance = DisplayAmount.natural(
                        entryRepository.balance(account.id).amount,
                        Denomination.exact(account.currency),
                    ),
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
                    limit = DisplayAmount.natural(
                        creditCard.limit,
                        Denomination.exact(creditCard.currency),
                    ),
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
            base = input.baseCurrency,
            today = input.today,
        ).let { if (maxCategories >= 0) it.take(maxCategories) else it }

        return if (categorySpending.isNotEmpty()) {
            DashboardComponent.SpendingByCategory(
                categorySpending = categorySpending.map { it.toUi() },
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
            base = input.baseCurrency,
            today = input.today,
        ).let { if (maxCategories >= 0) it.take(maxCategories) else it }

        return if (categoryIncome.isNotEmpty()) {
            DashboardComponent.IncomeByCategory(
                categoryIncome = categoryIncome.map { it.toUi() },
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
            today = input.today,
        )

        return if (budgetProgress.isNotEmpty()) {
            DashboardComponent.Budgets(
                budgetProgress = budgetProgress.map { it.toUi() },
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

            !recurring.isArchived &&
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
                transactions = recentTransactions.mapNotNull {
                    it.toTransactionUi(lookup = input.facadeLookup)
                },
                hasMore = presentTransactions.size > count,
            )
        } else {
            null
        }
    }

    /**
     * A widget figure as it is shown. Every one of them spans accounts, so the ledger answers
     * per currency and this is the single reduction to what the surface renders — the base and
     * the governing date both come from the input, so every widget of one build agrees.
     */
    private suspend fun DashboardComponentsInput.figure(balance: CurrencyBalance) =
        consolidateFigure(
            balance = balance,
            base = baseCurrency,
            date = consolidationDateOf(targetMonth, today),
            policy = DisplayAmount.SignPolicy.NATURAL,
        )

    /**
     * A budget's progress, denominated for the bar: the limit in the currency it was stated
     * in, and the spending already reduced to that same currency by the progress use case.
     */
    private fun BudgetProgress.toUi() = BudgetProgressUi(
        id = budget.id,
        title = budget.title,
        icon = budget.icon,
        spent = spent.figure,
        limit = DisplayAmount.magnitude(
            budget.amount,
            Denomination.exact(budget.currency),
        ),
        progress = progress,
    )

    /** A category's share, denominated for the card. The number and the share stay the domain's. */
    private fun CategorySpending.toUi() = CategorySpendingUi(
        category = category,
        amount = amount.figure,
        percentage = percentage,
    )

    private fun quickActions(config: Map<String, String>): DashboardComponent.QuickActions? {
        // On desktop the persistent rail already exposes every feature, so the quick-actions grid is
        // redundant — omit the whole section (rather than render an empty, header-only gap).
        if (isDesktop) return null

        val hiddenActions = parseHiddenActionKeys(config)

        val allActions = navCatalog.destinations.filter { !it.primaryTab }

        return DashboardComponent.QuickActions(actions = allActions.filter { it.actionKey !in hiddenActions })
    }
}
