package com.neoutils.finsight

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationRecurring
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Category
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

class ComposeAppCommonTest {

    @Test
    fun operationLabelFallsBackToUntitledWhenTitleAndCategoryAreMissing() {
        val operation = Operation(
            kind = Operation.Kind.TRANSACTION,
            title = null,
            date = LocalDate(2026, 3, 3),
            transactions = emptyList(),
        )

        assertEquals("Untitled", operation.label)
    }

    @Test
    fun recurringLabelFallsBackToUntitledWhenTitleAndCategoryAreMissing() {
        val recurring = Recurring(
            type = Recurring.Type.EXPENSE,
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
    fun operationRecurringLabelFallsBackToCategoryWhenTitleIsMissing() {
        val recurring = Recurring(
            type = Recurring.Type.EXPENSE,
            amount = 10.0,
            title = null,
            dayOfMonth = 3,
            category = Category(
                name = "Food",
                iconKey = "fastfood",
                type = Category.Type.EXPENSE,
                createdAt = 0L,
            ),
            account = null,
            creditCard = null,
            createdAt = 0L,
        )

        assertEquals("Food • 1", OperationRecurring(id = recurring.id, recurringLabel = recurring.label, cycleNumber = 1).label)
    }

    @Test
    fun operationRecurringLabelFallsBackToUntitledWhenTitleAndCategoryAreMissing() {
        val recurring = Recurring(
            type = Recurring.Type.EXPENSE,
            amount = 10.0,
            title = null,
            dayOfMonth = 3,
            category = null,
            account = null,
            creditCard = null,
            createdAt = 0L,
        )

        assertEquals("Untitled • 1", OperationRecurring(id = recurring.id, recurringLabel = recurring.label, cycleNumber = 1).label)
    }
}
