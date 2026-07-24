package com.neoutils.finsight.domain.model.form

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.extension.contraLegFor
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The coherence rule of `chart-of-accounts`: an expense category belongs to a
 * transaction that increases expense, and an income category to one that increases
 * income.
 *
 * It was enforced only where a picker filtered its list — three screens, plus a
 * private copy of the predicate in one of them — so nothing stopped an incoherent
 * pair reaching the ledger by any other route, and nothing said so out loud. The
 * rule has one owner (`Category.Type.isAccept`) applied by the form; these pin the
 * form, not the pickers.
 */
class TransactionFormCoherenceTest {

    private val account = Account(id = 1, name = "Checking")

    private fun category(type: Category.Type) = Category(
        id = 1,
        name = "Food",
        icon = CategoryLazyIcon("food"),
        type = type,
        createdAt = 0L,
        dimensionId = 7,
    )

    private fun form(type: TransactionType, category: Category?) = TransactionForm.from(
        type = type,
        amount = "1000",
        title = "Something",
        date = "01/03/2026",
        category = category,
        target = TransactionTarget.ACCOUNT,
        creditCard = null,
        invoiceDueMonth = null,
        account = account,
    )

    @Test
    fun `an expense category survives an expense`() {
        val kept = form(TransactionType.EXPENSE, category(Category.Type.EXPENSE)).category

        assertEquals(1L, kept?.id)
        assertEquals(Category.Type.EXPENSE, kept?.type)
    }

    @Test
    fun `an expense category is dropped from an income`() {
        assertNull(form(TransactionType.INCOME, category(Category.Type.EXPENSE)).category)
    }

    @Test
    fun `an income category is dropped from an expense`() {
        assertNull(form(TransactionType.EXPENSE, category(Category.Type.INCOME)).category)
    }

    @Test
    fun `an adjustment carries no category at all`() {
        // Neither nature accepts an adjustment: its counterpart is reconciliation.
        assertNull(form(TransactionType.ADJUSTMENT, category(Category.Type.EXPENSE)).category)
        assertNull(form(TransactionType.ADJUSTMENT, category(Category.Type.INCOME)).category)
    }

    /**
     * And the consequence downstream: because the form already dropped it, the leg
     * that reaches the ledger lands on the nominal of its *own* type, unclassified —
     * never on the nominal of a category the transaction cannot have.
     */
    @Test
    fun `a dropped category leaves the leg unclassified on its own nominal`() {
        val dropped = form(TransactionType.INCOME, category(Category.Type.EXPENSE))

        val contra = contraLegFor(dropped.type, dropped.category)

        assertEquals(AccountType.INCOME, contra.nature)
        assertNull(contra.dimensionId)
    }

    @Test
    fun `an adjustment posts to equity whatever the category was`() {
        val adjustment = form(TransactionType.ADJUSTMENT, category(Category.Type.EXPENSE))

        assertEquals(AccountType.EQUITY, contraLegFor(adjustment.type, adjustment.category).nature)
    }
}
