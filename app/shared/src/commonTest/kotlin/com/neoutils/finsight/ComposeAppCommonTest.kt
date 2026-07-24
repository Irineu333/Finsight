package com.neoutils.finsight

import com.neoutils.finsight.domain.model.TransactionRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun recurringLabelFallsBackToUntitledWhenTitleAndCategoryAreMissing() {
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

        assertEquals("Untitled", recurring.label)
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
    fun transactionRecurringLabelFallsBackToUntitledWhenTitleAndCategoryAreMissing() {
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

        assertEquals("Untitled • 1", TransactionRecurring(instance = recurring, cycleNumber = 1).label)
    }
}
