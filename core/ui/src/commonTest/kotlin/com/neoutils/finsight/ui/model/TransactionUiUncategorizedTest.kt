package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The flat answer a surface that filters display models depends on.
 *
 * It exists because `categoryId == null` cannot stand in for it: the three transactions
 * below have no category *and* no analytic axis, and a screen reading the null would call
 * them unclassified — the one mistake this field is here to prevent.
 */
class TransactionUiUncategorizedTest {

    private val wallet = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val savings = Account(id = 2, name = "Savings", type = AccountType.ASSET, currency = "BRL")
    private val card = Account(id = 3, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAcc = Account(id = 4, name = "expense", type = AccountType.EXPENSE, currency = "BRL")
    private val equityAcc = Account(id = 5, name = "reconciliation", type = AccountType.EQUITY, currency = "BRL")

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0, dimensionId = 70,
    )

    private val lookup = TransactionFacadeLookup.of(listOf(groceries), installments = emptyList())

    private fun entry(account: Account, amount: Long, dimensionId: Long? = null) =
        Entry(account = account, amount = amount, dimensionId = dimensionId)

    private fun ui(vararg entries: Entry) = Transaction(
        title = null,
        date = LocalDate(2026, 1, 10),
        entries = entries.toList(),
    ).toTransactionUi(lookup = lookup)

    @Test
    fun `an expense with no category is unclassified`() {
        val item = ui(entry(wallet, -5_000), entry(expenseAcc, 5_000))

        assertEquals(null, item?.categoryId)
        assertTrue(item?.isUncategorized == true)
    }

    @Test
    fun `a classified expense is not`() {
        val item = ui(entry(wallet, -5_000), entry(expenseAcc, 5_000, dimensionId = groceries.dimensionId))

        assertEquals(groceries.id, item?.categoryId)
        assertFalse(item?.isUncategorized == true)
    }

    @Test
    fun `a transfer has no category and is still not unclassified`() {
        val item = ui(entry(wallet, -10_000), entry(savings, 10_000))

        assertEquals(null, item?.categoryId, "the null a surface must not read as unclassified")
        assertFalse(item?.isUncategorized == true)
    }

    @Test
    fun `an invoice payment is not unclassified either`() {
        val item = ui(entry(wallet, -5_000), entry(card, 5_000))

        assertFalse(item?.isUncategorized == true)
    }

    @Test
    fun `an adjustment is not unclassified either`() {
        val item = ui(entry(wallet, 3_000), entry(equityAcc, -3_000))

        assertFalse(item?.isUncategorized == true)
    }

    @Test
    fun `an orphan dimension resolves to no category and is not unclassified`() {
        // The nastiest case for a display model: the lookup finds nothing, so `categoryId`
        // is null exactly as for a loose expense — and the two must not read alike.
        val item = ui(entry(wallet, -5_000), entry(expenseAcc, 5_000, dimensionId = 999))

        assertEquals(null, item?.categoryId)
        assertFalse(item?.isUncategorized == true)
    }
}
