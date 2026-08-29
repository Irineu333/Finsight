package com.neoutils.finsight.ui.screen.budgets

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.BudgetProgress
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * **What decides where a budget sits in the list.**
 *
 * The order used to come from `BudgetDao` — `ORDER BY createdAt ASC` — and answered no
 * question the screen asks. It is now the share of the ceiling already consumed, taken
 * after the progress has been computed, because the share is a reading of the ledger and
 * not a column of the budget.
 *
 * The two claims worth pinning down are the ones a naive sort gets wrong: that creation
 * order no longer has a say, and that a budget whose spending could not be reduced to the
 * limit's currency goes to the **end** rather than being ordered as a zero — which would
 * seat what nothing is known about among the budgets known to be untouched.
 */
class BudgetListOrderTest {

    private fun progress(
        id: Long,
        limit: Double,
        spent: Double,
        createdAt: Long,
        unpriced: Boolean = false,
    ) = BudgetProgress(
        budget = Budget(
            id = id,
            title = "Budget $id",
            categories = emptyList(),
            iconKey = "shopping",
            amount = limit,
            currency = "BRL",
            createdAt = createdAt,
        ),
        spent = spent,
        hasUnpricedSpending = unpriced,
    )

    private fun List<BudgetProgress>.ids() = map { it.budget.id }

    @Test
    fun `the exceeded budget comes first, whenever it was created`() {
        val calm = progress(id = 1L, limit = 300.0, spent = 45.0, createdAt = 1L)
        val exceeded = progress(id = 2L, limit = 300.0, spent = 380.0, createdAt = 99L)

        assertEquals(listOf(2L, 1L), listOf(calm, exceeded).sortedByConsumption().ids())
    }

    @Test
    fun `the share decides, and not the size of the ceiling`() {
        val big = progress(id = 1L, limit = 2_500.0, spent = 1_550.0, createdAt = 1L)
        val small = progress(id = 2L, limit = 300.0, spent = 240.0, createdAt = 2L)

        // 62% against 80%: the larger figure is the smaller share.
        assertEquals(listOf(2L, 1L), listOf(big, small).sortedByConsumption().ids())
    }

    /**
     * The clamped fraction the ring draws makes these two the same number. The order does
     * not read that fraction, precisely so that the worse overrun still outranks.
     */
    @Test
    fun `a threefold overrun outranks a budget that has just gone over`() {
        val justOver = progress(id = 1L, limit = 300.0, spent = 301.0, createdAt = 1L)
        val farOver = progress(id = 2L, limit = 300.0, spent = 900.0, createdAt = 2L)

        assertEquals(listOf(2L, 1L), listOf(justOver, farOver).sortedByConsumption().ids())
    }

    @Test
    fun `unresolved spending goes to the end, not among the least consumed`() {
        val calm = progress(id = 1L, limit = 300.0, spent = 45.0, createdAt = 1L)
        val unresolved = progress(
            id = 2L,
            limit = 400.0,
            spent = 0.0,
            createdAt = 2L,
            unpriced = true,
        )
        val busy = progress(id = 3L, limit = 1_200.0, spent = 960.0, createdAt = 3L)

        assertEquals(
            listOf(3L, 1L, 2L),
            listOf(calm, unresolved, busy).sortedByConsumption().ids(),
        )
    }

    @Test
    fun `budgets on the same share keep the order they were created in`() {
        val first = progress(id = 1L, limit = 300.0, spent = 150.0, createdAt = 1L)
        val second = progress(id = 2L, limit = 1_000.0, spent = 500.0, createdAt = 2L)

        assertEquals(listOf(1L, 2L), listOf(first, second).sortedByConsumption().ids())
    }

    /**
     * A `PERCENTAGE` ceiling whose base recurring is gone derives to zero. Nothing spent
     * against a ceiling of zero has no share to take, so it joins the unknowns at the end
     * instead of leading the list on a division that is not a number.
     */
    @Test
    fun `a ceiling of zero with nothing spent has no share`() {
        val zeroCeiling = progress(id = 1L, limit = 0.0, spent = 0.0, createdAt = 1L)
        val calm = progress(id = 2L, limit = 300.0, spent = 45.0, createdAt = 2L)

        assertEquals(listOf(2L, 1L), listOf(zeroCeiling, calm).sortedByConsumption().ids())
    }

    @Test
    fun `a ceiling of zero with something spent against it leads`() {
        val zeroCeiling = progress(id = 1L, limit = 0.0, spent = 30.0, createdAt = 1L)
        val exceeded = progress(id = 2L, limit = 300.0, spent = 380.0, createdAt = 2L)

        assertEquals(listOf(1L, 2L), listOf(exceeded, zeroCeiling).sortedByConsumption().ids())
    }
}
