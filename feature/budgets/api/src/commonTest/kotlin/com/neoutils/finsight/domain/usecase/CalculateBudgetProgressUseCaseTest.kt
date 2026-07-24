package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import com.neoutils.finsight.domain.repository.IEntryRepository
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the `spent` figure of [CalculateBudgetProgressUseCase]: Σ entries
 * carrying each budgeted category's dimension, in the month. The use case reads the
 * ledger itself now (task 9.2) — `:core:ledger` is a core, so an `api` may depend on
 * it — instead of being handed the map by three separate callers. The number (42.5)
 * must survive both moves.
 */
class CalculateBudgetProgressUseCaseTest {

    private val month = YearMonth(2026, 3)

    private fun useCase(balances: Map<Long, Double> = emptyMap()) =
        CalculateBudgetProgressUseCase(MonthBalances(month, balances))

    private fun category(id: Long, dimensionId: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = dimensionId,
    )

    private val budget = Budget(
        id = 1, title = "Food & Transport",
        categories = listOf(category(1, dimensionId = 10), category(2, dimensionId = 11)),
        iconKey = "shopping", amount = 200.0, limitType = LimitType.FIXED, createdAt = 0L,
    )

    @Test
    fun `spent sums the month's entries carrying the budget's category dimensions`() = runTest {
        // Nominal legs are debit-positive: the ledger read is already +spent.
        // Dimension 10 spent 30.0 and 11 spent 12.5 → 42.5. Dimension 12 is outside
        // the budget, and any other month is excluded by the read itself.
        val progress = useCase(mapOf(10L to 30.0, 11L to 12.5, 12L to 99.0))(
            budgets = listOf(budget),
            month = month,
        ).single()

        assertEquals(42.5, progress.spent)
        assertEquals(200.0, progress.budget.amount)
    }

    @Test
    fun `categories with no movement contribute nothing`() = runTest {
        val budgetWithUnposted = budget.copy(
            categories = listOf(category(1, dimensionId = 10), category(3, dimensionId = 12)),
        )

        val progress = useCase(mapOf(10L to 30.0))(
            budgets = listOf(budgetWithUnposted),
            month = month,
        ).single()

        assertEquals(30.0, progress.spent)
    }

    @Test
    fun `a percentage limit reads the confirmation of the month being looked at`() = runTest {
        val salary = Recurring(
            id = 7, type = TransactionType.INCOME, amount = 1000.0, title = "Salary",
            dayOfMonth = 5, category = null, account = null, creditCard = null, createdAt = 0L,
        )
        val percentageBudget = budget.copy(
            limitType = LimitType.PERCENTAGE, percentage = 50.0, recurringId = salary.id,
        )
        // March was confirmed at 2000 (a bonus); February was never confirmed and must
        // fall back to the recurring's own 1000 — reading March's confirmation there
        // would wrongly yield 1000.0 instead of 500.0.
        val marchConfirmation = confirmedTransaction(salary, LocalDate(2026, 3, 5), cents = 200_000)

        val february = useCase()(
            budgets = listOf(percentageBudget),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = YearMonth(2026, 2),
        ).single()

        assertEquals(500.0, february.budget.amount)

        val march = useCase()(
            budgets = listOf(percentageBudget),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = month,
        ).single()

        assertEquals(1000.0, march.budget.amount)
    }

    private fun confirmedTransaction(recurring: Recurring, date: LocalDate, cents: Long) = Transaction(
        id = 1, title = recurring.title, date = date,
        recurringId = recurring.id, recurringCycle = 1,
        entries = listOf(
            Entry(account = Account(id = 100, name = "Checking", type = AccountType.ASSET), amount = -cents),
            Entry(account = Account(id = 101, name = "Salary", type = AccountType.INCOME), amount = cents),
        ),
    )
}

/**
 * The one ledger read this use case makes: the month balance of each dimension it
 * is asked about. Anything not seeded reads zero, which is what "no movement" is.
 */
private class MonthBalances(
    private val month: YearMonth,
    private val balances: Map<Long, Double>,
) : IEntryRepository {
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double =
        if (month == this.month) balances[dimensionId] ?: 0.0 else 0.0

    // Nothing else is this use case's business; reaching any of it is the test
    // telling us the use case grew a dependency it did not declare.
    override suspend fun getEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long) = throw NotImplementedError()
    override fun observeLedgerChanges() = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?) = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long) = throw NotImplementedError()
    override suspend fun hasEntriesForDimension(dimensionId: Long) = throw NotImplementedError()
    override suspend fun balance(accountId: Long) = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long) = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionOwed(dimensionId: Long) = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long) = throw NotImplementedError()
    override suspend fun liabilityMonthFlows(month: YearMonth) = throw NotImplementedError()
    override suspend fun assetMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.AssetMonthFlows = throw NotImplementedError()
    override suspend fun netWorth() = throw NotImplementedError()
    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ) = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ) = throw NotImplementedError()
    override suspend fun scopeStats(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ) = throw NotImplementedError()
}
