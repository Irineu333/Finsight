package com.neoutils.finsight.ui.modal.payInvoice

import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The submit button of the invoice payment covering the **second** field (design D26).
 *
 * What the invoice owes is never on trial here — it is a fact. What is on trial is what
 * leaves the account, and only when the two are denominated differently.
 */
class PayInvoiceSubmitEnablementTest {

    private val closing = LocalDate(2026, 7, 1)
    private val due = LocalDate(2026, 7, 10)
    private val date = dayMonthYear.format(LocalDate(2026, 7, 5))

    @Test
    fun `paying a dollar invoice from a real account waits for what leaves it`() {
        assertFalse(
            isValidInvoicePayment(
                date = date,
                minDate = closing,
                maxDate = due,
                outstandingDebt = 100.0,
                paidAmount = "",
                isCrossCurrency = true,
            )
        )
    }

    @Test
    fun `a second field of zero is no more submittable than an empty one`() {
        assertFalse(
            isValidInvoicePayment(
                date = date,
                minDate = closing,
                maxDate = due,
                outstandingDebt = 100.0,
                paidAmount = "R$ 0,00",
                isCrossCurrency = true,
            )
        )
    }

    @Test
    fun `stating what leaves the account is submittable`() {
        assertTrue(
            isValidInvoicePayment(
                date = date,
                minDate = closing,
                maxDate = due,
                outstandingDebt = 100.0,
                paidAmount = "R$ 550,00",
                isCrossCurrency = true,
            )
        )
    }

    /** Same currency: the sheet is the one it always was, with no second field to fill. */
    @Test
    fun `a same-currency payment never waits for a second field`() {
        assertTrue(
            isValidInvoicePayment(
                date = date,
                minDate = closing,
                maxDate = due,
                outstandingDebt = 100.0,
                paidAmount = "",
                isCrossCurrency = false,
            )
        )
    }
}
