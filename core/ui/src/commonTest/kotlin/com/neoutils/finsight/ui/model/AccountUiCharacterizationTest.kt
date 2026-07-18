package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the legacy `AccountUi` sums (balance, opening, income, expense,
 * adjustment, invoice payment) over a representative account ledger. These are the
 * numbers the ledger aggregates (EntryDao.accountPeriodTotals, task 2.4) must
 * reproduce when task 4.4 flips `AccountUi` to the ledger — the dataset here mirrors
 * the one in core/database's AccountPeriodTotalsQueryTest, and the two must agree.
 */
class AccountUiCharacterizationTest {

    private val month = YearMonth(2026, 1)
    private val account = Account(id = 1, name = "Checking", type = AccountType.ASSET)

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    private fun leg(
        type: Transaction.Type,
        amount: Double,
        day: Int,
        invoice: Invoice? = null,
        yearMonth: YearMonth = month,
    ) = Transaction(
        type = type,
        amount = amount,
        title = null,
        date = LocalDate(yearMonth.year, yearMonth.month, day),
        account = account,
        invoice = invoice,
    )

    // The account's own legs, mirroring the six operation forms of the DB test:
    // income 100, expense 30, transfer-out 50 (typed EXPENSE by the legacy transfer),
    // adjustment +40, invoice payment 80, plus a prior-month expense 15 for opening.
    private val transactions = listOf(
        leg(Transaction.Type.INCOME, 100.0, day = 5),
        leg(Transaction.Type.EXPENSE, 30.0, day = 10),
        leg(Transaction.Type.EXPENSE, 50.0, day = 12),
        leg(Transaction.Type.ADJUSTMENT, 40.0, day = 15),
        leg(Transaction.Type.EXPENSE, 80.0, day = 20, invoice = invoice),
        leg(Transaction.Type.EXPENSE, 15.0, day = 20, yearMonth = YearMonth(2025, 12)),
    )

    @Test
    fun `account ui sums characterize the legacy figures`() {
        val ui = AccountUi(account = account, transactions = transactions, month = month)

        assertEquals(-15.0, ui.openingBalance, "opening = prior-month expense only")
        assertEquals(-35.0, ui.balance, "balance = Σ signed legs up to the month")
        assertEquals(100.0, ui.income)
        assertEquals(80.0, ui.expense, "expense includes the transfer-out leg, excludes the invoice payment")
        assertEquals(40.0, ui.adjustment)
        assertEquals(80.0, ui.invoicePayment)
    }
}
