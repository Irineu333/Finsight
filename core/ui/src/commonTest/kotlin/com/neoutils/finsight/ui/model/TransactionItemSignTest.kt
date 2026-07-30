package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.Denomination
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.DisplayAmount.SignPolicy
import com.neoutils.finsight.extension.format
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins the item surface's sign rule against the ledger that produced the figure.
 *
 * An **adjustment** is the defect this exists for: it is the only transaction whose
 * direction the label withholds, and it used to read `+` in every direction because the
 * mapper took the magnitude. It now carries the ledger's own sign — a debt that grows is
 * a credit on the liability leg, so it reads negative.
 *
 * The remaining forms are non-regression, one case per shape, because a form left
 * without an explicit policy would show up as a wrong sign rather than as a failure.
 */
class TransactionItemSignTest {

    private val formatter = CurrencyFormatter()

    private val account = Account(id = 1L, name = "Account", type = AccountType.ASSET)
    private val destination = Account(id = 2L, name = "Destination", type = AccountType.ASSET)
    private val card = Account(id = 3L, name = "Card", type = AccountType.LIABILITY)
    private val reconciliation = Account(id = 4L, name = "Reconciliation", type = AccountType.EQUITY)
    private val expense = Account(id = 5L, name = "Expenses", type = AccountType.EXPENSE)
    private val income = Account(id = 6L, name = "Income", type = AccountType.INCOME)

    private fun transactionOf(vararg entries: Entry) = Transaction(
        id = 1L,
        title = "Op",
        date = LocalDate(2026, 1, 1),
        entries = entries.toList(),
    )

    // region adjustment — the defect, in its four directions

    @Test
    fun anAdjustmentThatRaisesACardDebtReadsNegative() {
        // R$ 0,00 → R$ 100,00 of debt: a credit on the liability leg.
        val ui = transactionOf(
            Entry(account = card, amount = -10_000),
            Entry(account = reconciliation, amount = 10_000),
        ).toTransactionUi(accountId = card.id)

        assertEquals(DisplayAmount.explicitSign(-100.0, Denomination.exact("BRL")), ui?.amount)
        assertEquals("-" + formatter.format(100.0, "BRL"), formatter.format(ui!!.amount))
    }

    @Test
    fun anAdjustmentThatLowersACardDebtReadsPositive() {
        val ui = transactionOf(
            Entry(account = card, amount = 10_000),
            Entry(account = reconciliation, amount = -10_000),
        ).toTransactionUi(accountId = card.id)

        assertEquals(DisplayAmount.explicitSign(100.0, Denomination.exact("BRL")), ui?.amount)
        assertEquals("+" + formatter.format(100.0, "BRL"), formatter.format(ui!!.amount))
    }

    @Test
    fun anAdjustmentThatLowersAnAccountBalanceReadsNegative() {
        val ui = transactionOf(
            Entry(account = account, amount = -10_000),
            Entry(account = reconciliation, amount = 10_000),
        ).toTransactionUi(accountId = account.id)

        assertEquals(DisplayAmount.explicitSign(-100.0, Denomination.exact("BRL")), ui?.amount)
    }

    @Test
    fun anAdjustmentThatRaisesAnAccountBalanceReadsPositive() {
        val ui = transactionOf(
            Entry(account = account, amount = 10_000),
            Entry(account = reconciliation, amount = -10_000),
        ).toTransactionUi(accountId = account.id)

        assertEquals(DisplayAmount.explicitSign(100.0, Denomination.exact("BRL")), ui?.amount)
    }

    @Test
    fun anAdjustmentReadsTheSameWithAndWithoutPerspective() {
        val transaction = transactionOf(
            Entry(account = card, amount = -10_000),
            Entry(account = reconciliation, amount = 10_000),
        )

        assertEquals(
            transaction.toTransactionUi(accountId = card.id)?.amount,
            transaction.toTransactionUi()?.amount,
        )
    }

    // endregion

    // region non-regression — the labels that already give the direction

    @Test
    fun anExpenseOnAnAccountKeepsItsMagnitude() {
        val ui = transactionOf(
            Entry(account = account, amount = -10_000),
            Entry(account = expense, amount = 10_000),
        ).toTransactionUi(accountId = account.id)

        assertEquals(SignPolicy.MAGNITUDE, ui?.amount?.policy)
        assertEquals(100.0, ui?.amount?.value)
    }

    @Test
    fun anExpenseOnACardKeepsItsMagnitude() {
        val ui = transactionOf(
            Entry(account = card, amount = -10_000),
            Entry(account = expense, amount = 10_000),
        ).toTransactionUi(accountId = card.id)

        assertEquals(SignPolicy.MAGNITUDE, ui?.amount?.policy)
        assertEquals(100.0, ui?.amount?.value)
    }

    @Test
    fun anIncomeKeepsItsMagnitude() {
        val ui = transactionOf(
            Entry(account = account, amount = 10_000),
            Entry(account = income, amount = -10_000),
        ).toTransactionUi(accountId = account.id)

        assertEquals(SignPolicy.MAGNITUDE, ui?.amount?.policy)
        assertEquals(100.0, ui?.amount?.value)
    }

    @Test
    fun anInvoicePaymentKeepsItsMagnitude() {
        val ui = transactionOf(
            Entry(account = account, amount = -10_000),
            Entry(account = card, amount = 10_000),
        ).toTransactionUi(accountId = account.id)

        assertEquals(SignPolicy.MAGNITUDE, ui?.amount?.policy)
        assertEquals(100.0, ui?.amount?.value)
    }

    // endregion

    // region transfer — signed at both ends under a perspective, signless without one

    private val transfer = transactionOf(
        Entry(account = account, amount = -10_000),
        Entry(account = destination, amount = 10_000),
    )

    @Test
    fun aTransferUnderAPerspectiveIsSignedAtBothEnds() {
        val outgoing = transfer.toTransactionUi(accountId = account.id)
        val incoming = transfer.toTransactionUi(accountId = destination.id)

        assertEquals("-" + formatter.format(100.0, "BRL"), formatter.format(outgoing!!.amount))
        assertEquals("+" + formatter.format(100.0, "BRL"), formatter.format(incoming!!.amount))
    }

    @Test
    fun aTransferWithoutPerspectiveShowsNoSign() {
        val ui = transfer.toTransactionUi()

        assertEquals(SignPolicy.MAGNITUDE, ui?.amount?.policy)
        assertEquals(formatter.format(100.0, "BRL"), formatter.format(ui!!.amount))
    }

    // endregion
}
