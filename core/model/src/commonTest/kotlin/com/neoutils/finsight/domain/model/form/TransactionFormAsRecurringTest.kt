package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What a transaction has to satisfy to be written already covers what a template has
 * to satisfy to exist: an amount that is present and non-zero, a title **or** a
 * category, and somewhere for the money to move through.
 *
 * That containment is what lets the screen offer the recurring mark with no validation
 * of its own — instalments are the only thing in its way. If any of these falls, that
 * shortcut stopped being true, and the mark needs a rule the screen no longer has.
 */
class TransactionFormAsRecurringTest {

    private val date = LocalDate(2026, 3, 12)

    private val account = Account(id = 1, name = "Checking", currency = "BRL")

    private val creditCard = CreditCard(
        id = 1,
        accountId = 2,
        name = "Card",
        limit = 5000.0,
        closingDay = 20,
        dueDay = 27,
    )

    private fun category(type: Category.Type) = Category(
        id = 1,
        name = "Food",
        icon = CategoryLazyIcon("food"),
        type = type,
        createdAt = 0L,
        dimensionId = 7,
    )

    private fun form(
        type: TransactionType = TransactionType.EXPENSE,
        title: String? = "Rent",
        category: Category? = null,
        target: TransactionTarget = TransactionTarget.ACCOUNT,
    ) = TransactionForm.from(
        type = type,
        amount = "240000",
        title = title,
        date = "12/03/2026",
        category = category,
        target = target,
        creditCard = creditCard,
        invoiceDueMonth = YearMonth(2026, 3),
        account = account,
    )

    @Test
    fun `an expense on an account yields a valid template`() {
        assertTrue(form().asRecurringOn(date).isValid())
    }

    @Test
    fun `an expense on a card yields a valid template`() {
        val recurringForm = form(target = TransactionTarget.CREDIT_CARD).asRecurringOn(date)

        assertTrue(recurringForm.isValid())
        assertEquals(creditCard, recurringForm.creditCard)
    }

    @Test
    fun `an income on an account yields a valid template`() {
        assertTrue(form(type = TransactionType.INCOME).asRecurringOn(date).isValid())
    }

    @Test
    fun `a category with no title yields a valid template`() {
        val recurringForm = form(
            title = null,
            category = category(Category.Type.EXPENSE),
        ).asRecurringOn(date)

        assertTrue(recurringForm.isValid())
    }

    @Test
    fun `a title with no category yields a valid template`() {
        assertTrue(form(category = null).asRecurringOn(date).isValid())
    }

    /** The day of the repetition is the day of the transaction, and nothing else. */
    @Test
    fun `the template repeats on the day of the given date`() {
        assertEquals("12", form().asRecurringOn(date).dayOfMonth)
        assertEquals("28", form().asRecurringOn(LocalDate(2026, 2, 28)).dayOfMonth)
    }

    /**
     * The invoice is where *this* purchase landed. A future cycle lands on whichever
     * invoice is open when it is confirmed, so carrying this one over would be wrong.
     */
    @Test
    fun `the invoice is not carried into the template`() {
        val recurringForm = form(target = TransactionTarget.CREDIT_CARD).asRecurringOn(date)

        assertEquals(null, recurringForm.account)
        assertEquals(creditCard, recurringForm.creditCard)
    }
}
