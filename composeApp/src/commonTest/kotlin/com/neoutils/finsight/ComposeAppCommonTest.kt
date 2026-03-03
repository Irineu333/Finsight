package com.neoutils.finsight

import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
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
            type = Transaction.Type.EXPENSE,
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
}
