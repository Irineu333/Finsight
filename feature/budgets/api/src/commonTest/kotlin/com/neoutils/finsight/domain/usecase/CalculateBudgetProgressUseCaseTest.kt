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
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the `spent` figure of [CalculateBudgetProgressUseCase] (Σ entries of
 * the budget's category accounts in the month). Task 4.2 flipped this to the ledger:
 * the caller (`impl`, which owns the `IEntryRepository` dependency) reads the per
 * category-account month balances and passes them in as [categoryBalances]; this pure
 * use case sums them per budget. The number (42.5) must survive.
 */
class CalculateBudgetProgressUseCaseTest {

    private val useCase = CalculateBudgetProgressUseCase()
    private val month = YearMonth(2026, 3)

    private fun category(id: Long, accountId: Long?) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, accountId = accountId,
    )

    private val budget = Budget(
        id = 1, title = "Food & Transport",
        categories = listOf(category(1, accountId = 10), category(2, accountId = 11)),
        iconKey = "shopping", amount = 200.0, limitType = LimitType.FIXED, createdAt = 0L,
    )

    @Test
    fun `spent sums the month's entries in the budget category accounts`() {
        // EXPENSE category accounts are debit-positive: the ledger read is +spent.
        // Account 10 spent 30.0, account 11 spent 12.5 → 42.5. Account 12 (outside the
        // budget) and any other month are excluded by the ledger read itself.
        val categoryBalances = mapOf(10L to 30.0, 11L to 12.5, 12L to 99.0)

        val progress = useCase(
            budgets = listOf(budget),
            categoryBalances = categoryBalances,
            month = month,
        ).single()

        assertEquals(42.5, progress.spent)
        assertEquals(200.0, progress.budget.amount)
    }

    @Test
    fun `categories never posted to contribute nothing`() {
        val budgetWithUnposted = budget.copy(
            categories = listOf(category(1, accountId = 10), category(3, accountId = null)),
        )

        val progress = useCase(
            budgets = listOf(budgetWithUnposted),
            categoryBalances = mapOf(10L to 30.0),
            month = month,
        ).single()

        assertEquals(30.0, progress.spent)
    }

    @Test
    fun `a percentage limit reads the confirmation of the month being looked at`() {
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

        val february = useCase(
            budgets = listOf(percentageBudget),
            categoryBalances = emptyMap(),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = YearMonth(2026, 2),
        ).single()

        assertEquals(500.0, february.budget.amount)

        val march = useCase(
            budgets = listOf(percentageBudget),
            categoryBalances = emptyMap(),
            recurringList = listOf(salary),
            transactions = listOf(marchConfirmation),
            month = month,
        ).single()

        assertEquals(1000.0, march.budget.amount)
    }

    private fun confirmedTransaction(recurring: Recurring, date: LocalDate, cents: Long) = Transaction(
        id = 1, title = recurring.title, date = date,
        recurring = TransactionRecurring(instance = recurring, cycleNumber = 1),
        entries = listOf(
            Entry(account = Account(id = 100, name = "Checking", type = AccountType.ASSET), amount = -cents),
            Entry(account = Account(id = 101, name = "Salary", type = AccountType.INCOME), amount = cents),
        ),
    )
}
