package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
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
    private val today = LocalDate(2026, 3, 15)

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
            today = today,
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
            today = today,
        ).single()

        assertEquals(30.0, progress.spent)
    }
}
