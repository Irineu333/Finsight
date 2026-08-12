package com.neoutils.finsight.ui.modal.confirmRecurring

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the confirmation offers to classify a cycle with.
 *
 * Two rules meet here, and neither is stated in this file for the first time: coherence
 * between the transaction type and the category's own belongs to `isAccept`, and
 * continuity of an already-chosen facade is what keeps a category archived *after* the
 * template elected it from silently unclassifying the cycle.
 */
class OfferedCategoriesTest {

    private fun category(
        id: Long,
        name: String,
        type: Category.Type,
    ) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("shopping"),
        type = type,
        createdAt = 0L,
    )

    private val market = category(1, "Mercado", Category.Type.EXPENSE)
    private val pharmacy = category(2, "Farmácia", Category.Type.EXPENSE)
    private val salary = category(3, "Salário", Category.Type.INCOME)

    private val open = listOf(market, pharmacy, salary)

    @Test
    fun `an expense recurring is offered expense categories only`() {
        val offered = offeredCategories(open, TransactionType.EXPENSE, selected = null)

        assertEquals(listOf(market, pharmacy), offered)
    }

    @Test
    fun `an income recurring is offered income categories only`() {
        val offered = offeredCategories(open, TransactionType.INCOME, selected = null)

        assertEquals(listOf(salary), offered)
    }

    /**
     * The reason this function takes the selection at all: an archived category is out of
     * [open], so without adding it back the selector would open empty on a template that
     * has a category — dropping the classification without the user touching anything.
     */
    @Test
    fun `a category archived after being chosen is still offered, selected`() {
        val archived = category(9, "Streaming", Category.Type.EXPENSE).copy(isArchived = true)

        val offered = offeredCategories(open, TransactionType.EXPENSE, selected = archived)

        assertEquals(listOf(market, pharmacy, archived), offered)
    }

    @Test
    fun `an archived category is never offered as a fresh choice`() {
        val archived = category(9, "Streaming", Category.Type.EXPENSE).copy(isArchived = true)

        val offered = offeredCategories(open, TransactionType.EXPENSE, selected = null)

        assertTrue(archived !in offered, "dropped once, it is gone while it stays archived")
    }

    @Test
    fun `an open category already chosen is not offered twice`() {
        val offered = offeredCategories(open, TransactionType.EXPENSE, selected = market)

        assertEquals(listOf(market, pharmacy), offered)
    }
}
