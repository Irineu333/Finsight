package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * Characterizes the dashboard's own per-account balance (DashboardComponentsBuilder
 * `accountsOverview`, whose per-account sum was reimplemented outside the use case):
 * an all-time balance per account, plus the excluded-account filter. Task 4.5 flips
 * this to the ledger; the numbers must survive.
 */
class DashboardAccountsOverviewTest {

    private val accountA = Account(id = 1, name = "A", type = AccountType.ASSET, currency = "BRL")
    private val accountB = Account(id = 2, name = "B", type = AccountType.ASSET, currency = "BRL")

    private fun builder() = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = ThrowingEntryRepository),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(ThrowingEntryRepository, reducer()),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = ThrowingEntryRepository,
        accountRepository = FakeAccountRepository(),
        consolidateMoney = reducer(),
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private fun input(accounts: List<Account>) = DashboardComponentsInput(
        transactions = emptyList(),
        creditCards = emptyList(),
        invoicesByCreditCardId = emptyMap(),
        accounts = accounts,
        budgets = emptyList(),
        recurringList = emptyList(),
        occurrences = emptyList(),
        today = LocalDate(2026, 3, 20),
        targetMonth = YearMonth(2026, 3),
    )

    private suspend fun overview(accounts: List<Account>, config: Map<String, String>): List<DashboardAccountUi> {
        val component = builder().build(
            key = DashboardComponentType.ACCOUNTS_OVERVIEW.key,
            input = input(accounts),
            context = DashboardBuilderContext(pendingRecurring = emptyList()),
            config = config,
        )
        return (component as DashboardComponent.AccountsOverview).accounts
    }

    @Test
    fun `per-account balance sums all of the account's legs`() = runTest {
        val accounts = overview(
            accounts = listOf(accountA, accountB),
            config = mapOf(AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "false"),
        )
        assertEquals(70.0, accounts.first { it.id == 1L }.balance.value)
        assertEquals(30.0, accounts.first { it.id == 2L }.balance.value)
    }

    @Test
    fun `excluded accounts are dropped`() = runTest {
        val accounts = overview(
            accounts = listOf(accountA, accountB),
            config = mapOf(
                AccountsOverviewConfig.HIDE_SINGLE_ACCOUNT to "false",
                AccountsOverviewConfig.EXCLUDED_ACCOUNT_IDS to "2",
            ),
        )
        assertEquals(listOf(1L), accounts.map { it.id })
    }

    // --- dashboard month stats (task 3.11: sites :156,157 and :181,186) ---

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1, creditCard = card,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME, currency = "BRL")
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE, currency = "BRL")

    private fun transaction(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private fun statsEntries(counter: Account, assetAmount: Double, counterAmount: Double) =
        listOf(Entry(account = accountA, amount = (assetAmount * 100).toLong()), Entry(account = counter, amount = (counterAmount * 100).toLong()))

    @Test
    fun `concrete balance stats sum account income and expense for the month`() = runTest {
        val transactions = listOf(
            transaction(1, LocalDate(2026, 3, 5), statsEntries(incomeAcc, 100.0, -100.0)),
            transaction(2, LocalDate(2026, 3, 10), statsEntries(expenseAcc, -30.0, 30.0)),
            transaction(3, LocalDate(2026, 2, 5), statsEntries(incomeAcc, 999.0, -999.0)), // other month
        )
        val component = builder().build(
            key = DashboardComponentType.CONCRETE_BALANCE_STATS.key,
            input = input(listOf(accountA)).copy(transactions = transactions),
            context = DashboardBuilderContext(pendingRecurring = emptyList()),
            config = emptyMap(),
        )
        val stats = component as DashboardComponent.ConcreteBalanceStats
        assertEquals(100.0, stats.income.value)
        assertEquals(30.0, stats.expense.value)
    }

    @Test
    fun `credit card balance stats split payment and expense for the month`() = runTest {
        val component = builder().build(
            key = DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key,
            input = input(emptyList()),
            context = DashboardBuilderContext(pendingRecurring = emptyList()),
            config = emptyMap(),
        )
        val stats = component as DashboardComponent.CreditCardBalanceStats
        assertEquals(25.0, stats.payment.value)
        assertEquals(60.0, stats.expense.value)
    }

    /**
     * The budgets widget must read the recurring confirmation of the month on screen,
     * not of the current one: browsing March in July used to size a `PERCENTAGE` limit
     * from July's confirmation, because the builder passed `today` instead of `targetMonth`.
     */
    @Test
    fun `a percentage budget uses the confirmation of the month on screen`() = runTest {
        val salary = Recurring(
            id = 7, type = TransactionType.INCOME, amount = 1000.0, title = "Salary",
            dayOfMonth = 5, category = null, account = null, creditCard = null, createdAt = 0L,
        )
        val marchConfirmation = Transaction(
            id = 1, title = "Salary", date = LocalDate(2026, 3, 5),
            recurringId = salary.id, recurringCycle = 1,
            entries = listOf(
                Entry(account = accountA, amount = -200_000),
                Entry(account = Account(id = 3, name = "Salary", type = AccountType.INCOME, currency = "BRL"), amount = 200_000),
            ),
        )
        val budget = Budget(
            id = 1, title = "Half the salary", categories = emptyList(), iconKey = "shopping",
            amount = 0.0, currency = "BRL", limitType = LimitType.PERCENTAGE, percentage = 50.0,
            recurringId = salary.id, createdAt = 0L,
        )

        val component = builder().build(
            key = DashboardComponentType.BUDGETS.key,
            input = input(emptyList()).copy(
                budgets = listOf(budget),
                recurringList = listOf(salary),
                transactions = listOf(marchConfirmation),
                today = LocalDate(2026, 7, 19),
                targetMonth = YearMonth(2026, 3),
            ),
            context = DashboardBuilderContext(pendingRecurring = emptyList()),
            config = emptyMap(),
        )

        val budgets = component as DashboardComponent.Budgets
        assertEquals(1000.0, budgets.budgetProgress.single().budget.amount)
    }
}

private object ThrowingEntryRepository : IEntryRepository {
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    // All-time per-account balance the accounts-overview reads (task 4.5): account 1 =
    // 100 − 30 = 70, account 2 = 50 − 20 = 30 — the figures the screen showed before.
    override suspend fun balance(accountId: Long): Double = mapOf(1L to 70.0, 2L to 30.0).getValue(accountId)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    // Month-wide card stats the credit-card balance widget reads (task 4.11): expense 60, payment 25.
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth) =
        com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency(
            expense = MoneyByCurrency.of("BRL", 60.0),
            payment = MoneyByCurrency.of("BRL", 25.0),
            adjustment = MoneyByCurrency.zero,
        )

    // Month-wide asset income/expense the concrete-balance widget reads (spec `ledger-reporting`):
    // March holds income 100, expense 30; other months are empty — and "empty" is the
    // figure with no currency at all, which is not the same fact as zero in one.
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?) =
        if (month == YearMonth(2026, 3)) {
            com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency(
                income = MoneyByCurrency.of("BRL", 100.0),
                yield = MoneyByCurrency.zero,
                expense = MoneyByCurrency.of("BRL", 30.0),
                adjustment = MoneyByCurrency.zero,
            )
        } else {
            com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency.zero
        }

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}
