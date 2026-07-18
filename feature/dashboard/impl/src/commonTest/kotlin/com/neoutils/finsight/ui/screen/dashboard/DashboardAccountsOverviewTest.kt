package com.neoutils.finsight.ui.screen.dashboard

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.feature.shell.api.NavCatalog
import com.neoutils.finsight.feature.shell.api.NavDestination
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.InvoiceUi
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBudgetProgressUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategoryIncomeUseCase
import com.neoutils.finsight.domain.usecase.CalculateCategorySpendingUseCase
import com.neoutils.finsight.domain.usecase.CalculateTransactionStatsUseCase
import com.neoutils.finsight.domain.usecase.GetPendingRecurringUseCase
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the dashboard's own per-account balance (DashboardComponentsBuilder
 * `accountsOverview`, the `Σ signedCents / 100` reimplemented outside the use case):
 * an all-time balance per account, plus the excluded-account filter. Task 4.5 flips
 * this to the ledger; the numbers must survive.
 */
class DashboardAccountsOverviewTest {

    private val accountA = Account(id = 1, name = "A", type = AccountType.ASSET)
    private val accountB = Account(id = 2, name = "B", type = AccountType.ASSET)

    private fun leg(type: Transaction.Type, amount: Double, account: Account, day: Int) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, 3, day), account = account,
    )

    private val allTransactions = listOf(
        leg(Transaction.Type.INCOME, 100.0, accountA, 5),
        leg(Transaction.Type.EXPENSE, 30.0, accountA, 10),
        leg(Transaction.Type.INCOME, 50.0, accountB, 5),
        leg(Transaction.Type.ADJUSTMENT, -20.0, accountB, 12),
    )

    private fun builder() = DashboardComponentsBuilder(
        calculateBalanceUseCase = CalculateBalanceUseCase(entryRepository = ThrowingEntryRepository),
        calculateTransactionStatsUseCase = CalculateTransactionStatsUseCase(),
        calculateCategorySpendingUseCase = object : CalculateCategorySpendingUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateCategoryIncomeUseCase = object : CalculateCategoryIncomeUseCase {
            override suspend fun invoke(forYearMonth: YearMonth): List<CategorySpending> = throw NotImplementedError()
        },
        calculateBudgetProgressUseCase = CalculateBudgetProgressUseCase(),
        getPendingRecurringUseCase = GetPendingRecurringUseCase(),
        invoiceUiMapper = object : InvoiceUiMapper {
            override suspend fun toUi(invoice: Invoice): InvoiceUi = throw NotImplementedError()
        },
        entryRepository = ThrowingEntryRepository,
        navCatalog = object : NavCatalog { override val destinations: List<NavDestination> = emptyList() },
    )

    private fun input(accounts: List<Account>) = DashboardComponentsInput(
        operations = emptyList(),
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
            context = DashboardBuilderContext(allTransactions = allTransactions, pendingRecurring = emptyList()),
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
        assertEquals(70.0, accounts.first { it.account.id == 1L }.balance)
        assertEquals(30.0, accounts.first { it.account.id == 2L }.balance)
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
        assertEquals(listOf(1L), accounts.map { it.account.id })
    }

    // --- dashboard month stats (task 3.11: sites :156,157 and :181,186) ---

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1, creditCard = card,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private val incomeAcc = Account(id = 100, name = "income", type = AccountType.INCOME)
    private val expenseAcc = Account(id = 101, name = "expense", type = AccountType.EXPENSE)

    private fun singleLegOperation(id: Long, leg: Transaction, entries: List<Entry> = emptyList()) =
        Operation(id = id, title = null, date = leg.date, transactions = listOf(leg), entries = entries)

    private fun statsEntries(counter: Account, assetAmount: Double, counterAmount: Double) =
        listOf(Entry(account = accountA, amount = (assetAmount * 100).toLong()), Entry(account = counter, amount = (counterAmount * 100).toLong()))

    @Test
    fun `concrete balance stats sum account income and expense for the month`() = runTest {
        val operations = listOf(
            singleLegOperation(1, leg(Transaction.Type.INCOME, 100.0, accountA, 5), statsEntries(incomeAcc, 100.0, -100.0)),
            singleLegOperation(2, leg(Transaction.Type.EXPENSE, 30.0, accountA, 10), statsEntries(expenseAcc, -30.0, 30.0)),
            singleLegOperation(3, leg(Transaction.Type.INCOME, 999.0, accountA, 5).copy(date = LocalDate(2026, 2, 5)), statsEntries(incomeAcc, 999.0, -999.0)), // other month
        )
        val component = builder().build(
            key = DashboardComponentType.CONCRETE_BALANCE_STATS.key,
            input = input(listOf(accountA)).copy(operations = operations),
            context = DashboardBuilderContext(allTransactions = operations.flatMap { it.transactions }, pendingRecurring = emptyList()),
            config = emptyMap(),
        )
        val stats = component as DashboardComponent.ConcreteBalanceStats
        assertEquals(100.0, stats.income)
        assertEquals(30.0, stats.expense)
    }

    @Test
    fun `credit card balance stats split payment and expense for the month`() = runTest {
        val cardLegs = listOf(
            Transaction(type = Transaction.Type.EXPENSE, amount = 60.0, title = null, date = LocalDate(2026, 3, 8), creditCard = card, invoice = invoice),
            Transaction(type = Transaction.Type.INCOME, amount = 25.0, title = null, date = LocalDate(2026, 3, 20), creditCard = card, invoice = invoice), // payment
            Transaction(type = Transaction.Type.EXPENSE, amount = 999.0, title = null, date = LocalDate(2026, 2, 8), creditCard = card, invoice = invoice), // other month
        )
        val component = builder().build(
            key = DashboardComponentType.CREDIT_CARD_BALANCE_STATS.key,
            input = input(emptyList()),
            context = DashboardBuilderContext(allTransactions = cardLegs, pendingRecurring = emptyList()),
            config = emptyMap(),
        )
        val stats = component as DashboardComponent.CreditCardBalanceStats
        assertEquals(25.0, stats.payment)
        assertEquals(60.0, stats.expense)
    }
}

private object ThrowingEntryRepository : IEntryRepository {
    override suspend fun getEntriesByOperation(operationId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    // All-time per-account balance the accounts-overview reads (task 4.5): account 1 =
    // 100 − 30 = 70, account 2 = 50 − 20 = 30, matching the legacy Σ signedCents.
    override suspend fun balance(accountId: Long): Double = mapOf(1L to 70.0, 2L to 30.0).getValue(accountId)
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
    // Month-wide card stats the credit-card balance widget reads (task 4.11): expense 60, payment 25.
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows =
        com.neoutils.finsight.domain.repository.CardMonthFlows(expense = 60.0, payment = 25.0)
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}
