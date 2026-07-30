package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.AssetMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.test.StubEntryRepository
import com.neoutils.finsight.test.brl
import com.neoutils.finsight.test.brlBalance
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Characterizes the dashboard's own per-account balance (DashboardComponentsBuilder
 * `accountsOverview`, whose per-account sum was reimplemented outside the use case):
 * an all-time balance per account, plus the excluded-account filter. Task 4.5 flips
 * this to the ledger; the numbers must survive.
 */
class DashboardAccountsOverviewTest {

    private val accountA = Account(currency = "BRL", id = 1, name = "A", type = AccountType.ASSET)
    private val accountB = Account(currency = "BRL", id = 2, name = "B", type = AccountType.ASSET)

    private fun builder() = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = ThrowingEntryRepository),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(ThrowingEntryRepository),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice, cardInvoices: List<Invoice>): InvoiceUi =
                throw NotImplementedError()
        },
        entryRepository = ThrowingEntryRepository,
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
        assertEquals(70.0, accounts.first { it.id == 1L }.balance)
        assertEquals(30.0, accounts.first { it.id == 2L }.balance)
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

    private val incomeAcc = Account(currency = "BRL", id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(currency = "BRL", id = 101, name = "expense", type = AccountType.EXPENSE)

    private fun transaction(id: Long, date: LocalDate, entries: List<Entry>) =
        Transaction(id = id, title = null, date = date, entries = entries)

    private fun statsEntries(counter: Account, assetAmount: Double, counterAmount: Double) =
        listOf(Entry(currency = "BRL", account = accountA, amount = (assetAmount * 100).toLong()), Entry(currency = "BRL", account = counter, amount = (counterAmount * 100).toLong()))

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
        assertEquals(100.0, stats.income)
        assertEquals(30.0, stats.expense)
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
        assertEquals(25.0, stats.payment)
        assertEquals(60.0, stats.expense)
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
                Entry(currency = "BRL", account = accountA, amount = -200_000),
                Entry(currency = "BRL", account = Account(currency = "BRL", id = 3, name = "Salary", type = AccountType.INCOME), amount = 200_000),
            ),
        )
        val budget = Budget(
            id = 1, title = "Half the salary", categories = emptyList(), iconKey = "shopping",
            amount = 0.0, limitType = LimitType.PERCENTAGE, percentage = 50.0,
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

private object ThrowingEntryRepository : StubEntryRepository() {
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    // All-time per-account balance the accounts-overview reads (task 4.5): account 1 =
    // 100 − 30 = 70, account 2 = 50 − 20 = 30 — the figures the screen showed before.
    override suspend fun balance(accountId: Long) = brlBalance(mapOf(1L to 70.0, 2L to 30.0).getValue(accountId))
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    // Month-wide card stats the credit-card balance widget reads (task 4.11): expense 60, payment 25.
    override suspend fun liabilityMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.LiabilityMonthFlows =
        com.neoutils.finsight.domain.repository.LiabilityMonthFlows(expense = brl(60.0), payment = brl(25.0), adjustment = brl(0.0))
    // Month-wide asset income/expense the concrete-balance widget reads (spec `ledger-reporting`):
    // March holds income 100, expense 30; other months are empty.
    override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows =
        if (month == YearMonth(2026, 3)) com.neoutils.finsight.domain.repository.AssetMonthFlows(income = brl(100.0), expense = brl(30.0), adjustment = brl(0.0))
        else com.neoutils.finsight.domain.repository.AssetMonthFlows(income = brl(0.0), expense = brl(0.0), adjustment = brl(0.0))
}
