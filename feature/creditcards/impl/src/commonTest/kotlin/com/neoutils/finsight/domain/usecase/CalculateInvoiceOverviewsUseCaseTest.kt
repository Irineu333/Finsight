package com.neoutils.finsight.domain.usecase

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
 * Characterizes [CalculateInvoiceOverviewsUseCase]: expense/advancePayment/adjustment
 * and the debit-positive owed total of the card legs of an invoice closing in a given
 * month. Task 4.11 flips this to the ledger; the numbers must survive.
 */
class CalculateInvoiceOverviewsUseCaseTest {

    private val useCase = CalculateInvoiceOverviewsUseCase()
    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)

    private fun invoice(id: Long, closing: Int) = Invoice(
        id = id, creditCard = card,
        openingMonth = YearMonth(2026, closing - 1), closingMonth = YearMonth(2026, closing), dueMonth = YearMonth(2026, closing + 1),
        status = Invoice.Status.OPEN,
    )

    private fun cardLeg(type: Transaction.Type, amount: Double, invoice: Invoice) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, 3, 10), creditCard = card, invoice = invoice,
    )

    @Test
    fun `invoice overview classifies the card legs and totals the owed`() {
        val march = invoice(id = 1, closing = 3)
        val april = invoice(id = 2, closing = 4)
        val transactions = listOf(
            cardLeg(Transaction.Type.EXPENSE, 60.0, march),
            cardLeg(Transaction.Type.EXPENSE, 40.0, march),
            cardLeg(Transaction.Type.ADJUSTMENT, 10.0, march),
            cardLeg(Transaction.Type.INCOME, 30.0, march), // advance payment (income card leg with invoice)
            cardLeg(Transaction.Type.EXPENSE, 999.0, april), // other invoice → excluded
            // Account leg → not a card leg → excluded.
            Transaction(type = Transaction.Type.EXPENSE, amount = 5.0, title = null, date = LocalDate(2026, 3, 2), account = Account(id = 1, name = "A", type = AccountType.ASSET)),
        )

        val stats = useCase(invoices = listOf(march, april), transactions = transactions, forYearMonth = YearMonth(2026, 3))
        val overview = stats.invoiceOverviews.single()

        assertEquals(100.0, overview.expense)
        assertEquals(30.0, overview.advancePayment)
        assertEquals(10.0, overview.adjustment)
        assertEquals(60.0, overview.total, "owed = +expense -adjustment -income")
        assertEquals(100.0, stats.creditCardOverview.expense)
        assertEquals(60.0, stats.creditCardOverview.total)
    }
}
