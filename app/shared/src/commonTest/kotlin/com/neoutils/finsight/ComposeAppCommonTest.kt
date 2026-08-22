package com.neoutils.finsight

import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ComposeAppCommonTest {

    /**
     * A template with neither a title nor a category cannot be written — `RecurringForm`
     * is the single owner of that rule and every write path goes through it. So reading
     * one is a state that should not exist, and the read says so instead of handing the
     * screen a name the user never chose.
     */
    @Test
    fun recurringLabelRefusesToNameATemplateThatHasNeitherTitleNorCategory() {
        val recurring = Recurring(
            type = TransactionType.EXPENSE,
            amount = 10.0,
            title = null,
            dayOfMonth = 3,
            category = null,
            account = null,
            creditCard = null,
            createdAt = 0L,
        )

        assertFailsWith<IllegalStateException> { recurring.label }
    }

    @Test
    fun transactionRecurringLabelFallsBackToCategoryWhenTitleIsMissing() {
        val recurring = Recurring(
            type = TransactionType.EXPENSE,
            amount = 10.0,
            title = null,
            dayOfMonth = 3,
            category = Category(
                name = "Food",
                icon = CategoryLazyIcon("fastfood"),
                type = Category.Type.EXPENSE,
                createdAt = 0L,
            ),
            account = null,
            creditCard = null,
            createdAt = 0L,
        )

        assertEquals("Food • 1", TransactionRecurring(instance = recurring, cycleNumber = 1).label)
    }

    @Test
    fun transactionRecurringLabelRefusesTheSameStateItsInstanceRefuses() {
        val recurring = Recurring(
            type = TransactionType.EXPENSE,
            amount = 10.0,
            title = null,
            dayOfMonth = 3,
            category = null,
            account = null,
            creditCard = null,
            createdAt = 0L,
        )

        assertFailsWith<IllegalStateException> {
            TransactionRecurring(instance = recurring, cycleNumber = 1).label
        }
    }
}
