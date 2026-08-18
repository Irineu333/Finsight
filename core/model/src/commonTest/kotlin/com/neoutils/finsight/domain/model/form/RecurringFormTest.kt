package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionType
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The amount a template repeats every month, and the one rule it has to satisfy to be one.
 *
 * `type` says whether the cycle takes money out or brings it in, so the amount carries no
 * direction of its own: a negative one is the same cycle posted on the other side of the
 * ledger, and it sleeps in the template until the first confirmation writes it. Zero and
 * less than zero are refused by the same rule because they are the same mistake.
 */
class RecurringFormTest {

    private val account = Account(id = 1, name = "Checking", currency = "BRL")

    @Test
    fun `an amount is required`() {
        assertEquals(RecurringError.AMOUNT_REQUIRED, form(amount = "").toRecurring(0L).leftOrNull())
    }

    @Test
    fun `zero is refused`() {
        assertEquals(
            RecurringError.AMOUNT_NOT_POSITIVE,
            form(amount = "0").toRecurring(0L).leftOrNull(),
        )
    }

    @Test
    fun `less than zero is refused by the same rule`() {
        assertEquals(
            RecurringError.AMOUNT_NOT_POSITIVE,
            form(amount = "-4000").toRecurring(0L).leftOrNull(),
        )
    }

    @Test
    fun `a positive amount is the template's own`() {
        assertEquals(40.0, form(amount = "4000").toRecurring(0L).getOrNull()?.amount)
    }

    @Test
    fun `the cheap reading the UI takes agrees with the rule`() {
        assertEquals(false, form(amount = "-4000").isValid())
        assertEquals(true, form(amount = "4000").isValid())
    }

    private fun form(amount: String) = RecurringForm(
        type = TransactionType.EXPENSE,
        amount = amount,
        title = "Netflix",
        dayOfMonth = "5",
        account = account,
        creditCard = null,
        category = null,
    )
}
