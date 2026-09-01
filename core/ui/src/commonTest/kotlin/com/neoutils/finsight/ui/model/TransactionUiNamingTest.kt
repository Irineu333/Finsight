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

/**
 * The half of the naming rule the mapper owns: the operation's own title, then its
 * category, then nothing.
 *
 * Nothing, and not a reserve literal — the third link is the surface's, because a list
 * item names the operation on its own while a header that already announced the nature
 * omits the line. A mapper that chose a text here would force one of the two to be wrong.
 */
class TransactionUiNamingTest {

    private val wallet = Account(id = 1, name = "Wallet", type = AccountType.ASSET, currency = "BRL")
    private val savings = Account(id = 2, name = "Savings", type = AccountType.ASSET, currency = "BRL")
    private val expenseAcc = Account(id = 4, name = "expense", type = AccountType.EXPENSE, currency = "BRL")

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0, dimensionId = 70,
    )

    private val lookup = TransactionFacadeLookup.of(listOf(groceries), installments = emptyList())

    private fun entry(account: Account, amount: Long, dimensionId: Long? = null) =
        Entry(account = account, amount = amount, dimensionId = dimensionId)

    private fun ui(title: String?, vararg entries: Entry) = Transaction(
        title = title,
        date = LocalDate(2026, 1, 10),
        entries = entries.toList(),
    ).toTransactionUi(lookup = lookup)

    @Test
    fun `a title of its own wins over the category`() {
        val item = ui(
            "Feira da esquina",
            entry(wallet, -5_000),
            entry(expenseAcc, 5_000, dimensionId = groceries.dimensionId),
        )

        assertEquals("Feira da esquina", item?.title)
    }

    @Test
    fun `without a title the category names it`() {
        val item = ui(
            null,
            entry(wallet, -5_000),
            entry(expenseAcc, 5_000, dimensionId = groceries.dimensionId),
        )

        assertEquals("Groceries", item?.title)
    }

    @Test
    fun `a blank title is an absence, not a name made of spaces`() {
        val item = ui(
            "   ",
            entry(wallet, -5_000),
            entry(expenseAcc, 5_000, dimensionId = groceries.dimensionId),
        )

        assertEquals("Groceries", item?.title)
    }

    @Test
    fun `with neither the mapper answers nothing, and names no absence`() {
        val item = ui(null, entry(wallet, -10_000), entry(savings, 10_000))

        assertEquals(null, item?.title, "the third link belongs to the surface that shows it")
    }

    @Test
    fun `a transfer with a title is named by it, and not by its form`() {
        val item = ui("Reserva de emergência", entry(wallet, -10_000), entry(savings, 10_000))

        assertEquals("Reserva de emergência", item?.title)
    }
}
