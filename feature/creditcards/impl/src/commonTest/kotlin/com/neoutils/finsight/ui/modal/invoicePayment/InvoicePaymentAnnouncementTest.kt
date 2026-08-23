package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.invoice_payment_advance
import com.neoutils.finsight.resources.invoice_payment_edit_confirm
import com.neoutils.finsight.resources.invoice_payment_edit_title
import com.neoutils.finsight.resources.invoice_payment_pay
import com.neoutils.finsight.resources.invoice_payment_title
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * What the sheet **announces** is the only thing that tells the two modes apart, and it
 * follows the mode rather than the selection.
 *
 * The mode is fixed from the opening, so the head holds still while the selectors below
 * it change — which is the same rule the head already obeyed, now with something to
 * distinguish. And an operation already written is not re-decided into a discharge by
 * the state of the invoice picked to correct it.
 */
class InvoicePaymentAnnouncementTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun invoice(id: Long, status: Invoice.Status) = Invoice(
        id = id,
        creditCard = card,
        dimensionId = id * 100,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 2),
        status = status,
    )

    private fun content(
        invoice: Invoice?,
        isEditMode: Boolean,
    ) = InvoicePaymentUiState.Content(
        selectedCreditCard = card,
        selectedInvoice = invoice,
        outstandingDebt = 800.0,
        invoiceCurrency = "BRL",
        today = LocalDate(2026, 1, 20),
        isEditMode = isEditMode,
    )

    @Test
    fun `a correction announces itself and says what confirming does`() {
        val state = content(invoice(1, Invoice.Status.OPEN), isEditMode = true)

        assertEquals(Res.string.invoice_payment_edit_title, state.headline)
        assertEquals(Res.string.invoice_payment_edit_confirm, state.label)
    }

    @Test
    fun `the announcement does not follow the invoice selected`() {
        val onOne = content(invoice(1, Invoice.Status.OPEN), isEditMode = true)
        val onAnother = content(invoice(2, Invoice.Status.RETROACTIVE), isEditMode = true)
        val onNone = content(invoice = null, isEditMode = true)

        listOf(onOne, onAnother, onNone).forEach { state ->
            assertEquals(Res.string.invoice_payment_edit_title, state.headline)
            assertEquals(Res.string.invoice_payment_edit_confirm, state.label)
        }
    }

    @Test
    fun `a registration keeps announcing the operation and the verb the state decides`() {
        val open = content(invoice(1, Invoice.Status.OPEN), isEditMode = false)
        val closed = content(invoice(2, Invoice.Status.CLOSED), isEditMode = false)

        assertEquals(Res.string.invoice_payment_title, open.headline)
        assertEquals(Res.string.invoice_payment_advance, open.label)

        assertEquals(Res.string.invoice_payment_title, closed.headline)
        assertEquals(Res.string.invoice_payment_pay, closed.label)
    }

    @Test
    fun `a correction is never re-decided into a discharge`() {
        // The sheet does not offer a closed invoice in this mode; if some path did, the
        // operation would still be the part it was written as.
        val state = content(invoice(2, Invoice.Status.CLOSED), isEditMode = true)

        assertFalse(state.settles)
        assertEquals(Res.string.invoice_payment_edit_confirm, state.label)
    }
}
