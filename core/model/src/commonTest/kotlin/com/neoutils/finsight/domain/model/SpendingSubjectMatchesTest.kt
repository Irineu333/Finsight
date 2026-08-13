package com.neoutils.finsight.domain.model

import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single definition of what a value of the analytic axis contains.
 *
 * The cases worth fixing are the ones that separate "unclassified" from "outside the
 * axis": a transfer, a payment and an adjustment have no nominal leg, and reading
 * `nominalDimensionId == null` alone would hand all three to the unclassified cut — which
 * would then disagree with the unclassified total it exists to explain.
 */
class SpendingSubjectMatchesTest {

    private fun account(id: Long, type: AccountType) =
        Account(id = id, name = "acc$id", type = type, currency = "BRL")

    private fun entry(
        type: AccountType,
        amount: Long,
        accountId: Long = type.ordinal.toLong(),
        dimensionId: Long? = null,
    ) = Entry(account = account(accountId, type), amount = amount, dimensionId = dimensionId)

    private fun transaction(vararg entries: Entry) = Transaction(
        title = null,
        date = LocalDate(2026, 1, 15),
        entries = entries.toList(),
    )

    private fun category(id: Long, name: String, type: Category.Type = Category.Type.EXPENSE) =
        Category(
            id = id,
            name = name,
            icon = CategoryLazyIcon("food"),
            type = type,
            createdAt = 0,
            dimensionId = id,
        )

    private val groceries = category(1, "Mercado")
    private val salary = category(2, "Salário", Category.Type.INCOME)

    @Test
    fun `an expense with no dimension on its nominal leg is unclassified`() {
        val expense = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.EXPENSE, 5_000),
        )

        assertTrue(expense.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `an income with no dimension on its nominal leg is unclassified too`() {
        val income = transaction(
            entry(AccountType.ASSET, 5_000),
            entry(AccountType.INCOME, -5_000),
        )

        assertTrue(income.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `a transfer is outside the axis, not unclassified`() {
        val transfer = transaction(
            entry(AccountType.ASSET, -10_000, accountId = 1),
            entry(AccountType.ASSET, 10_000, accountId = 2),
        )

        assertFalse(transfer.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `an invoice payment is outside the axis`() {
        val payment = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.LIABILITY, 5_000),
        )

        assertFalse(payment.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `a balance adjustment is outside the axis`() {
        val adjustment = transaction(
            entry(AccountType.ASSET, 3_000),
            entry(AccountType.EQUITY, -3_000),
        )

        assertFalse(adjustment.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `a categorized expense matches its own category and nothing else`() {
        val expense = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.EXPENSE, 5_000, dimensionId = groceries.dimensionId),
        )

        assertTrue(expense.matches(SpendingSubject.Categorized(groceries)))
        assertFalse(expense.matches(SpendingSubject.Categorized(salary)))
        assertFalse(expense.matches(SpendingSubject.Uncategorized))
    }

    @Test
    fun `an orphan dimension falls outside every value of the axis`() {
        // A nominal leg tagged with a dimension no category holds: an integrity failure,
        // and integrity failures are not absences of classification.
        val orphan = transaction(
            entry(AccountType.ASSET, -5_000),
            entry(AccountType.EXPENSE, 5_000, dimensionId = 99),
        )

        assertFalse(orphan.matches(SpendingSubject.Uncategorized))
        assertFalse(orphan.matches(SpendingSubject.Categorized(groceries)))
        assertFalse(orphan.matches(SpendingSubject.Categorized(salary)))
    }
}
