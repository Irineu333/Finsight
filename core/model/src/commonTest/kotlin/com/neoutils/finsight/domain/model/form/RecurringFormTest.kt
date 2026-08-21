package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

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

    /**
     * **A template cannot be persisted classified under a category that cannot classify it.**
     *
     * `toRecurring` validates a template, and it settles both halves of what the direction can
     * carry: an income has no card, whatever the form was holding, and no expense category. Every
     * caller that persists reaches here — including the ones that build the form through its
     * constructor rather than through `from`.
     *
     * The template has to come back **Right**: asserting only that the category is absent would
     * be satisfied by a refusal, which is a different outcome and would hide this rule the day
     * another one starts refusing first.
     */
    @Test
    fun `an income template keeps no expense category`() {
        val groceries = category(id = 1, name = "Mercado", type = Category.Type.EXPENSE)

        val template = RecurringForm(
            type = TransactionType.INCOME,
            amount = "500000",
            title = "Salário",
            dayOfMonth = "5",
            account = account,
            creditCard = null,
            category = groceries,
        ).toRecurring(createdAt = 0L)

        val persisted = assertNotNull(
            template.getOrNull(),
            "the template was refused rather than filtered: ${template.leftOrNull()}",
        )
        assertNull(
            persisted.category,
            "an income template was persisted classified under an expense category",
        )
    }

    /** The other direction of the same rule, which nothing else in the suite exercises. */
    @Test
    fun `an expense template keeps no income category`() {
        val salary = category(id = 2, name = "Salário", type = Category.Type.INCOME)

        val template = RecurringForm(
            type = TransactionType.EXPENSE,
            amount = "30000",
            title = "Aluguel",
            dayOfMonth = "5",
            account = account,
            creditCard = null,
            category = salary,
        ).toRecurring(createdAt = 0L)

        val persisted = assertNotNull(
            template.getOrNull(),
            "the template was refused rather than filtered: ${template.leftOrNull()}",
        )
        assertNull(persisted.category)
    }

    /** The mirror, so the rule is a rule and not a blanket drop. */
    @Test
    fun `an income template keeps an income category`() {
        val salary = category(id = 2, name = "Salário", type = Category.Type.INCOME)

        val template = RecurringForm(
            type = TransactionType.INCOME,
            amount = "500000",
            title = "Salário",
            dayOfMonth = "5",
            account = account,
            creditCard = null,
            category = salary,
        ).toRecurring(createdAt = 0L)

        assertEquals(salary, template.getOrNull()?.category)
    }

    private fun category(id: Long, name: String, type: Category.Type) = Category(
        id = id,
        name = name,
        icon = CategoryLazyIcon("tag"),
        type = type,
        createdAt = 0L,
    )

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
