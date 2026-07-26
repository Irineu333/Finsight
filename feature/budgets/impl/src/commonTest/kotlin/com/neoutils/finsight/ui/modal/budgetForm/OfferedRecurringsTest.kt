package com.neoutils.finsight.ui.modal.budgetForm

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The base-income selector must let a budget keep — and swap — the recurring it
 * already elected after that recurring is archived, without ever offering an
 * archived one for a new budget. Same rule as `OfferedCategoriesTest`, same form.
 */
class OfferedRecurringsTest {

    private fun recurring(id: Long, isArchived: Boolean = false) = Recurring(
        id = id,
        type = TransactionType.INCOME,
        amount = 1000.0,
        title = "Rec$id",
        dayOfMonth = 5,
        category = null,
        account = null,
        creditCard = null,
        createdAt = 0L,
        isArchived = isArchived,
    )

    private val salary = recurring(1)
    private val freelance = recurring(2)
    private val archived = recurring(3, isArchived = true)

    @Test
    fun `an archived recurring is not offered to a new budget`() {
        // A new budget has nothing selected, so the archived one has no claim.
        assertEquals(
            listOf(salary, freelance),
            offeredRecurrings(open = listOf(salary, freelance), selected = null),
        )
    }

    @Test
    fun `a budget that already elected it keeps seeing it`() {
        // It is not in the open list, but this budget already holds it — without this
        // the field would show nothing and the budget would lose its base income.
        assertEquals(
            listOf(salary, archived),
            offeredRecurrings(open = listOf(salary), selected = archived),
        )
    }

    @Test
    fun `it can be swapped for an open one`() {
        assertEquals(
            listOf(salary, freelance),
            offeredRecurrings(open = listOf(salary, freelance), selected = freelance),
        )
    }

    @Test
    fun `once swapped away it is not offerable again while archived`() {
        assertEquals(
            listOf(salary),
            offeredRecurrings(open = listOf(salary), selected = salary),
        )
    }

    @Test
    fun `the selected archived one is offered even when nothing is open`() {
        assertEquals(
            listOf(archived),
            offeredRecurrings(open = emptyList(), selected = archived),
        )
    }
}
