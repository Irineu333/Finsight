package com.neoutils.finsight.ui.modal.createInvoice

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.InvoiceMonthSelection
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The submit button of the creation sheet.
 *
 * A month that already has an invoice stays visible and navigable — only the submission
 * goes away. Hiding or skipping it would take from the user the sense of where they are in
 * the calendar, and the more invoices a card has the bigger the jump would be.
 */
class CreateInvoiceSubmitEnablementTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1_000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun stateFor(existingInvoice: Invoice?) = CreateInvoiceUiState(
        selection = InvoiceMonthSelection(
            creditCard = card,
            dueMonth = YearMonth(2026, 3),
            existingInvoice = existingInvoice,
        ),
        isLoaded = true,
    )

    @Test
    fun `a free month is submittable`() {
        assertTrue(stateFor(existingInvoice = null).canSubmit)
    }

    /** Before the card's invoices are read, "free" is not something the sheet knows yet. */
    @Test
    fun `an unread card is not submittable`() {
        assertFalse(
            CreateInvoiceUiState(
                selection = InvoiceMonthSelection(
                    creditCard = card,
                    dueMonth = YearMonth(2026, 3),
                    existingInvoice = null,
                )
            ).canSubmit
        )
    }

    @Test
    fun `an occupied month is not submittable, whatever its status`() {
        Invoice.Status.entries.forEach { status ->
            val state = stateFor(
                existingInvoice = Invoice(
                    id = 1,
                    creditCard = card,
                    openingMonth = YearMonth(2026, 2),
                    closingMonth = YearMonth(2026, 3),
                    dueMonth = YearMonth(2026, 3),
                    status = status,
                )
            )

            assertFalse(state.canSubmit, "a $status invoice still occupies the month")
        }
    }

    /** What the sheet shows before creating is the window the invoice is created with. */
    @Test
    fun `the window shown is the one the card derives`() {
        val state = stateFor(existingInvoice = null)

        assertEquals(YearMonth(2026, 2), state.window.openingMonth)
        assertEquals(YearMonth(2026, 3), state.window.closingMonth)
    }
}
