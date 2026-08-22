package com.neoutils.finsight.ui.mapper

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * How an instalment is named: its own title, then its category, then nothing.
 *
 * Nothing is the point of the third case. The screen names it by its form — an
 * instalment *is* one — and no generic reserve literal stands in for the absence, which
 * is what would otherwise reach a Portuguese screen written in English.
 *
 * The rule that a charge has a title or a category is only a screen's here (it enables a
 * button, nothing refuses the write), so this reads `null` rather than failing.
 */
class InstallmentUiNamingTest {

    private val cardAccount =
        Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAccount =
        Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private val groceries = Category(
        id = 7, name = "Groceries", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0, dimensionId = 70,
    )

    private val installment = Installment(id = 1, count = 2, totalAmount = 200.0)

    private fun charge(title: String?, nominalDimensionId: Long?) = Transaction(
        id = 1,
        title = title,
        date = LocalDate(2026, 3, 1),
        installmentId = installment.id,
        installmentNumber = 1,
        entries = listOf(
            Entry(transactionId = 1, account = cardAccount, amount = -10_000, dimensionId = 1),
            Entry(
                transactionId = 1,
                account = expenseAccount,
                amount = 10_000,
                dimensionId = nominalDimensionId,
            ),
        ),
    )

    private fun title(title: String?, nominalDimensionId: Long?): String? =
        InstallmentUiMapper().toUi(
            installment = installment,
            transactions = listOf(charge(title, nominalDimensionId)),
            lookup = TransactionFacadeLookup.of(listOf(groceries)),
            invoicesByDimension = emptyMap(),
        )?.title

    @Test
    fun `its own title wins over the category`() {
        assertEquals("Geladeira", title("Geladeira", nominalDimensionId = groceries.dimensionId))
    }

    @Test
    fun `without a title the category names it`() {
        assertEquals("Groceries", title(null, nominalDimensionId = groceries.dimensionId))
    }

    @Test
    fun `with neither, the name is absent and no reserve literal takes its place`() {
        assertEquals(
            null,
            title(null, nominalDimensionId = null),
            "the screen names it by its form; the mapper invents no text",
        )
    }
}
