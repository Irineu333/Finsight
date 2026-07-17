package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the `spent` figure of [CalculateBudgetProgressUseCase] (Σ amount of
 * the month's expense legs whose category is in the budget). Task 4.2 flips this to
 * the ledger (categoryTotals of the budget's category accounts); the number must
 * survive.
 */
class CalculateBudgetProgressUseCaseTest {

    private val useCase = CalculateBudgetProgressUseCase()
    private val today = LocalDate(2026, 3, 15)

    private fun category(id: Long) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L,
    )

    private fun leg(type: Transaction.Type, amount: Double, categoryId: Long, month: Int) = Transaction(
        type = type, amount = amount, title = null,
        date = LocalDate(2026, month, 10), category = category(categoryId),
    )

    private val budget = Budget(
        id = 1, title = "Food & Transport",
        categories = listOf(category(1), category(2)),
        iconKey = "shopping", amount = 200.0, limitType = LimitType.FIXED, createdAt = 0L,
    )

    @Test
    fun `spent sums the month's expense legs in the budget categories`() {
        val transactions = listOf(
            leg(Transaction.Type.EXPENSE, 30.0, categoryId = 1, month = 3),
            leg(Transaction.Type.EXPENSE, 12.5, categoryId = 2, month = 3),
            leg(Transaction.Type.EXPENSE, 40.0, categoryId = 1, month = 2), // other month → excluded
            leg(Transaction.Type.INCOME, 100.0, categoryId = 1, month = 3), // not an expense → excluded
            leg(Transaction.Type.EXPENSE, 99.0, categoryId = 3, month = 3), // outside budget → excluded
        )

        val progress = useCase(budgets = listOf(budget), transactions = transactions, today = today).single()

        assertEquals(42.5, progress.spent)
        assertEquals(200.0, progress.budget.amount)
    }
}
