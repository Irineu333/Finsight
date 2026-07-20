package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.error.ClosedFacade
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A form seeded from an old transaction can point at an archived leg, which the
 * write boundary refuses. The form declines to offer the submit instead of letting
 * the user press it and read an error.
 */
class TransactionFormArchivedTest {

    private fun account(isArchived: Boolean = false) =
        Account(id = 1, name = "Bank", type = AccountType.ASSET, isArchived = isArchived)

    private fun category(isArchived: Boolean = false) = Category(
        id = 1,
        name = "Food",
        icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE,
        createdAt = 0,
        isArchived = isArchived,
    )

    private fun card(isArchived: Boolean = false) = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 1,
        dueDay = 10,
        isArchived = isArchived,
    )

    private fun form(
        category: Category? = category(),
        account: Account? = account(),
        creditCard: CreditCard? = null,
        target: TransactionTarget = TransactionTarget.ACCOUNT,
    ) = TransactionForm.from(
        type = TransactionType.EXPENSE,
        amount = "50,00",
        title = "Groceries",
        date = "01/01/2026",
        category = category,
        target = target,
        creditCard = creditCard,
        invoiceDueMonth = null,
        account = account,
    )

    @Test
    fun `a form with no archived leg is valid`() {
        val form = form()
        assertTrue(form.archivedSelections.isEmpty())
        assertTrue(form.isValid())
    }

    @Test
    fun `an archived category leaves the form valid`() {
        // A category is not monetary: closing one strands no money, so it blocks
        // nothing the ledger cares about. This is the edit case — the user is
        // fixing an old transaction whose category was archived meanwhile, and
        // refusing it froze the transaction for a rule that does not exist.
        val form = form(category = category(isArchived = true))
        assertTrue(form.archivedSelections.isEmpty())
        assertTrue(form.isValid())
    }

    @Test
    fun `an archived account invalidates the form`() {
        val form = form(account = account(isArchived = true))
        assertEquals(setOf(ClosedFacade.ACCOUNT), form.archivedSelections)
        assertFalse(form.isValid())
    }

    @Test
    fun `an archived credit card invalidates the form`() {
        val form = form(
            account = null,
            creditCard = card(isArchived = true),
            target = TransactionTarget.CREDIT_CARD,
        )
        assertEquals(setOf(ClosedFacade.CREDIT_CARD), form.archivedSelections)
        assertFalse(form.isValid())
    }

    @Test
    fun `an archived category alongside an archived account reports only the account`() {
        // The set is the whole answer, and a category is never in it — otherwise a
        // caller naming what is wrong would send the user to change a category that
        // was never the problem.
        val form = form(
            category = category(isArchived = true),
            account = account(isArchived = true),
        )
        assertEquals(setOf(ClosedFacade.ACCOUNT), form.archivedSelections)
    }
}
