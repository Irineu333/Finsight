package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CurrencyBalance
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

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
        CalculateBudgetProgressUseCase(
            entryRepository = MonthBalances(month, balances),
            // No rate is on file, which is exactly right for a single-currency profile: the
            // consolidation passes one currency straight through, exact.
            consolidateFigure = ConsolidateFigureUseCase(NoRates),
        )

    private fun category(id: Long, dimensionId: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = dimensionId,
    )

    private val budget = Budget(
        id = 1, title = "Food & Transport",
        categories = listOf(category(1, dimensionId = 10), category(2, dimensionId = 11)),
        iconKey = "shopping", amount = 200.0, currency = "BRL", limitType = LimitType.FIXED, createdAt = 0L,
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

        assertEquals(42.5, progress.spent.comparable)
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

        assertEquals(30.0, progress.spent.comparable)
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
            Entry(currency = "BRL", account = Account(currency = "BRL", id = 100, name = "Checking", type = AccountType.ASSET), amount = -cents),
            Entry(currency = "BRL", account = Account(currency = "BRL", id = 101, name = "Salary", type = AccountType.INCOME), amount = cents),
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
) : StubEntryRepository() {
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): CurrencyBalance =
        CurrencyBalance.of("BRL", if (month == this.month) balances[dimensionId] ?: 0.0 else 0.0)
}

/** No rate at all — the single-currency profile every case here exercises. */
private object NoRates : com.neoutils.finsight.domain.repository.IExchangeRateRepository {
    override suspend fun rateOn(currency: String, date: LocalDate) = null
    override fun observeAll() = throw NotImplementedError()
    override suspend fun getAll() = throw NotImplementedError()
    override suspend fun record(rate: com.neoutils.finsight.domain.model.ExchangeRate) = throw NotImplementedError()
    override suspend fun remove(rate: com.neoutils.finsight.domain.model.ExchangeRate) = throw NotImplementedError()
}
