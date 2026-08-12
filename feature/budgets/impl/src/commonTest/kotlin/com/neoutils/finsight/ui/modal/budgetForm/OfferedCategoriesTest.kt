package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The selector applies a single rule: continuity of an already-made choice. An archived
 * category can be removed from the budget it is already in, and is never offered for a
 * new one. Belonging to another budget takes nothing off the list.
 */
class OfferedCategoriesTest {

    private fun category(id: Long, isArchived: Boolean = false) = Category(
        id = id, name = "Cat$id", icon = CategoryLazyIcon("shopping"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = id * 10, isArchived = isArchived,
    )

    private val food = category(1)
    private val transport = category(2)
    private val archived = category(3, isArchived = true)

    @Test
    fun `a category held by another budget is still offered`() {
        // A budget is a lens, not a slice: `transport` being watched by another budget
        // is no reason to keep it out of this one.
        val offered = offeredCategories(
            open = listOf(food, transport),
            selected = emptyList(),
        )
        assertEquals(listOf(food, transport), offered)
    }

    @Test
    fun `a selected archived category is offered so it can be removed`() {
        // It is not in the open list, but this budget already holds it — the dropdown
        // has to show it (checked) or it can never be unchecked.
        val offered = offeredCategories(
            open = listOf(food),
            selected = listOf(food, archived),
        )
        assertEquals(listOf(food, archived), offered)
    }

    @Test
    fun `an archived category not selected is never offered`() {
        // The whole point of archiving: it is gone once removed, and never offered
        // fresh. `selected` no longer holds it, so it does not come back.
        val offered = offeredCategories(
            open = listOf(food),
            selected = listOf(food),
        )
        assertEquals(listOf(food), offered)
    }

    @Test
    fun `a selected archived category is offered even when open is empty`() {
        val offered = offeredCategories(
            open = emptyList(),
            selected = listOf(archived),
        )
        assertEquals(listOf(archived), offered)
    }
}
