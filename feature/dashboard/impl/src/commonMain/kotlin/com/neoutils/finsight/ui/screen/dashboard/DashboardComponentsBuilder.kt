package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.repository.IEntryRepository
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
    private val calculateCategorySpendingUseCase: CalculateCategorySpendingUseCase,
    private val calculateCategoryIncomeUseCase: CalculateCategoryIncomeUseCase,
    private val calculateBudgetProgressUseCase: CalculateBudgetProgressUseCase,
    private val getPendingRecurringUseCase: GetPendingRecurringUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val entryRepository: IEntryRepository,
    private val accountRepository: IAccountRepository,
    // The one reducer, and the only place the base currency is allowed to surface: every
    // widget below that spans accounts leaves through it, denominated and marked by it,
    // rather than being tagged with the base at the point of formatting (design D29).
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val navCatalog: NavCatalog,
) {

    /**
     * A figure of the month displayed, consolidated at that month's rates — the past
     * would move on its own if a figure about March were reduced at today's.
     *
     * What goes in is what the ledger answered, per currency; what comes out carries the
     * currency, the exactness, and — where a rate is missing — more than one term.
     */
    private suspend fun DashboardComponentsInput.figure(
        money: MoneyByCurrency,
        policy: (Double, String, Boolean) -> DisplayAmount,
    ): ConsolidatedAmount = consolidateMoney(
        money = money,
        on = targetMonth.lastDay,
        policy = policy,
    )

    /**
     * A figure of money the ledger did not answer for — the pending total of a set of
     * recurring templates, which are facade rows and not entries.
     *
     * Each template is denominated by the account or card it names (design D17), so the
     * total is grouped by that currency and never by the base. A template whose source
     * was deleted names nothing and is left out, exactly as the list of them is.
     */
    private suspend fun List<Recurring>.moneyByCurrency(): MoneyByCurrency =
        fold(MoneyByCurrency.zero) { total, recurring ->
            val currency = currencyOf(recurring) ?: return@fold total
            total + MoneyByCurrency.of(currency, recurring.amount)
        }

    /** The account or card a template posts to is what denominates its amount (D17). */
    private suspend fun currencyOf(recurring: Recurring): String? =
        recurring.account?.currency
            ?: recurring.creditCard?.let { accountRepository.getAccountById(it.accountId)?.currency }

    /**
     * Nothing worth a widget: no term of the figure is positive.
     *
     * The per-currency form of the `<= 0.0` the scalar reads used. A widget hidden when
     * empty stays hidden only when it is empty in **every** currency — one dollar of
     * expense is a reason to show it, whatever the reais did.
     */
    private val MoneyByCurrency.isNothing: Boolean
        get() = toList().all { it.value <= 0.0 }

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
        // It spans every account, so it is a balance and it is consolidated: only the
        // negative is information.
        return DashboardComponent.TotalBalance(
            amount = input.figure(
                calculateBalanceUseCase(target = input.targetMonth),
                DisplayAmount::natural,
            ),
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
        val asset = entryRepository.assetMonthFlowsByCurrency(input.targetMonth)
        val liability = entryRepository.liabilityMonthFlowsByCurrency(input.targetMonth)

        val income = asset.income
        // Each currency summed with its own, by the ledger's one implementation — never
        // a map added up in line here, and never the consolidation layer's, which
        // answers for conversion and nothing else.
        val expense = asset.expense + liability.expense

        val isEmpty = income.isNothing && expense.isNothing
        if (isEmpty && config.hideWhenEmpty(defaultValue = false)) {
            return null
        }

        // Each widget carries its direction in its own label and icon, so the figures
        // read as magnitudes — the same text the plain formatting produced before.
        return DashboardComponent.OverallBalanceStats(
            income = input.figure(income, DisplayAmount::magnitude),
            expense = input.figure(expense, DisplayAmount::magnitude),
        )
    }

    private suspend fun concreteBalanceStats(
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.ConcreteBalanceStats? {
        // Month-wide income/expense across the user's ASSET accounts, straight from the
        // ledger — transfers and card payments (money between the user's own accounts)
        // are excluded there, not re-derived here (spec `ledger-reporting`).
        val stats = entryRepository.assetMonthFlowsByCurrency(input.targetMonth)

        val isEmpty = stats.income.isNothing && stats.expense.isNothing
        if (isEmpty && config.hideWhenEmpty(defaultValue = false)) {
            return null
        }

        return DashboardComponent.ConcreteBalanceStats(
            income = input.figure(stats.income, DisplayAmount::magnitude),
            expense = input.figure(stats.expense, DisplayAmount::magnitude),
        )
    }

    private suspend fun pendingBalanceStats(
        pendingRecurring: List<Recurring>,
        input: DashboardComponentsInput,
        config: Map<String, String>,
    ): DashboardComponent.PendingBalanceStats? {
        val pendingIncome = pendingRecurring.filter { it.type.isIncome }.moneyByCurrency()
        val pendingExpense = pendingRecurring.filter { it.type.isExpense }.moneyByCurrency()
        val isEmpty = pendingIncome.isNothing && pendingExpense.isNothing

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
            DashboardComponent.PendingBalanceStats(
                pendingIncome = input.figure(pendingIncome, DisplayAmount::magnitude),
                pendingExpense = input.figure(pendingExpense, DisplayAmount::magnitude),
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
        val flows = entryRepository.liabilityMonthFlowsByCurrency(input.targetMonth)
        val payment = flows.payment
        val expense = flows.expense

        val isEmpty = payment.isNothing && expense.isNothing

        return if (!isEmpty || !config.hideWhenEmpty(defaultValue = true)) {
            DashboardComponent.CreditCardBalanceStats(
                payment = input.figure(payment, DisplayAmount::magnitude),
                expense = input.figure(expense, DisplayAmount::magnitude),
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
                // in-builder per-account sum that used to live here. One account, one
                // currency — its own — and nothing was converted to get here.
                DashboardAccountUi(
                    id = account.id,
                    iconKey = account.iconKey,
                    name = account.name,
                    isDefault = account.isDefault,
                    balance = DisplayAmount.natural(
                        entryRepository.balance(account.id),
                        account.currency,
                        isApproximate = false,
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
            .mapNotNull { creditCard ->
                // The limit is the card's money, so it reads in the card's currency (D17),
                // and the LIABILITY account behind the card is the only place that states
                // it. A card whose account does not resolve is an orphan row: it is left
                // out of the pager rather than denominated by guess.
                val currency = accountRepository.getAccountById(creditCard.accountId)
                    ?.currency ?: return@mapNotNull null

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
                val limit = DisplayAmount.magnitude(
                    creditCard.limit,
                    currency,
                    isApproximate = false,
                )
                Triple(ui, invoice, limit)
            }

        return when {
            creditCardsWithBills.isNotEmpty() -> DashboardComponent.CreditCardsPager.Content(
                creditCards = creditCardsWithBills.map { it.first },
                domainInvoices = creditCardsWithBills.map { it.second },
                limits = creditCardsWithBills.map { it.third },
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
                targetMonth = input.targetMonth,
            )
        } else {
            null
        }
    }

    private suspend fun pendingRecurring(
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

        // One template posts to one account or one card, so its amount is that account's
        // money — exact, and never the base (design D17, D29). A template whose source was
        // deleted names no account, and a row no account denominates is left out rather
        // than shown in a guessed currency.
        val recurringUi = visibleRecurring.mapNotNull { recurring ->
            val currency = currencyOf(recurring) ?: return@mapNotNull null

            PendingRecurringUi(
                recurring = recurring,
                amount = DisplayAmount.magnitude(
                    recurring.amount,
                    currency,
                    isApproximate = false,
                ),
            )
        }

        return if (recurringUi.isNotEmpty()) {
            DashboardComponent.PendingRecurring(recurringList = recurringUi)
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

    private fun quickActions(config: Map<String, String>): DashboardComponent.QuickActions? {
        // On desktop the persistent rail already exposes every feature, so the quick-actions grid is
        // redundant — omit the whole section (rather than render an empty, header-only gap).
        if (isDesktop) return null

        val hiddenActions = parseHiddenActionKeys(config)

        val allActions = navCatalog.destinations.filter { !it.primaryTab }

        return DashboardComponent.QuickActions(actions = allActions.filter { it.actionKey !in hiddenActions })
    }
}
