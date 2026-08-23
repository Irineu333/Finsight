package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * One submit rule for one operation, covering both modes.
 *
 * The **second** field is the part easiest to forget (design D26): it is what keeps the
 * write boundary's same-sign guard unreachable by any path a user can walk. The ceiling,
 * meanwhile, holds over the card's side only — what leaves the account carries none,
 * because a limit there would be a limit expressed in the wrong currency.
 *
 * Where the payment discharges the invoice, the amount is not merely capped by what is
 * owed: it *is* what is owed, and the rule refuses anything else even though the field
 * states rather than accepts it.
 */
class InvoicePaymentSubmitEnablementTest {

    private val cycle = LocalDate(2026, 7, 1)..LocalDate(2026, 7, 20)
    private val settlement = LocalDate(2026, 7, 1)..LocalDate(2026, 7, 10)

    private val inCycle = dayMonthYear.format(LocalDate(2026, 7, 5))

    // A retroactive cycle: its whole window lies months behind today.
    private val pastCycle = LocalDate(2026, 1, 5)..LocalDate(2026, 2, 5)
    private val inPastCycle = dayMonthYear.format(LocalDate(2026, 1, 20))

    // -- Discharging a closed invoice -------------------------------------------------

    @Test
    fun `paying a dollar invoice from a real account waits for what leaves it`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "US$ 100,00",
                paidAmount = "",
                isCrossCurrency = true,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `a second field of zero is no more submittable than an empty one`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "US$ 100,00",
                paidAmount = "R$ 0,00",
                isCrossCurrency = true,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `stating what leaves the account is submittable`() {
        assertTrue(
            canSubmitInvoicePayment(
                amount = "US$ 100,00",
                paidAmount = "R$ 550,00",
                isCrossCurrency = true,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    /** Same currency: the sheet is the one it always was, with no second field to fill. */
    @Test
    fun `a same-currency payment never waits for a second field`() {
        assertTrue(
            canSubmitInvoicePayment(
                amount = "R$ 100,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `a closed invoice does not confirm an amount other than what it owes`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 40,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 140,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    // -- Paying part of an invoice still taking spending -------------------------------

    @Test
    fun `a cross-currency partial payment waits for what leaves the account`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "US$ 50,00",
                paidAmount = "",
                isCrossCurrency = true,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `the ceiling holds over the card's side and not over the account's`() {
        // 275 reais leaving to settle 50 dollars of a 100-dollar invoice: the account
        // side is far above the invoice's number and that is not a comparison at all.
        assertTrue(
            canSubmitInvoicePayment(
                amount = "US$ 50,00",
                paidAmount = "R$ 275,00",
                isCrossCurrency = true,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 100.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "US$ 150,00",
                paidAmount = "R$ 825,00",
                isCrossCurrency = true,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `a same-currency partial payment never waits for a second field`() {
        assertTrue(
            canSubmitInvoicePayment(
                amount = "R$ 50,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 100.0,
            )
        )
    }

    // -- Correcting a payment already registered ---------------------------------------

    @Test
    fun `a correction confirms above what the invoice currently owes`() {
        // The invoice was spent R$ 800 and R$ 300 of it are already paid by this very
        // operation, so it currently owes R$ 500. The ceiling the form is judged by
        // leaves that operation out and is R$ 800, which is what makes R$ 700 a
        // correction rather than a refusal.
        assertTrue(
            canSubmitInvoicePayment(
                amount = "R$ 700,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 800.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 900,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 800.0,
            )
        )
    }

    @Test
    fun `a correction pointed at another invoice is judged by that invoice's ceiling`() {
        // Nothing is left out there: the operation settled nothing on that invoice, so
        // the ceiling is simply what it owes — R$ 120.
        assertTrue(
            canSubmitInvoicePayment(
                amount = "R$ 120,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 120.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 300,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 120.0,
            )
        )
    }

    @Test
    fun `a retroactive invoice confirms a part of it, dated inside its own past cycle`() {
        assertTrue(
            canSubmitInvoicePayment(
                amount = "R$ 300,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inPastCycle,
                window = pastCycle,
                outstandingDebt = 800.0,
            )
        )
    }

    @Test
    fun `a date the window does not admit is not submittable in either mode`() {
        val afterTheCycle = dayMonthYear.format(LocalDate(2026, 7, 25))

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 50,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = afterTheCycle,
                window = cycle,
                outstandingDebt = 100.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 100,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = true,
                date = afterTheCycle,
                window = settlement,
                outstandingDebt = 100.0,
            )
        )
    }

    // -- Neither mode pays an invoice that owes nothing --------------------------------

    @Test
    fun `an invoice without debt is not payable in either mode`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 0,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = true,
                date = inCycle,
                window = settlement,
                outstandingDebt = 0.0,
            )
        )

        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 50,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = cycle,
                outstandingDebt = 0.0,
            )
        )
    }

    @Test
    fun `no invoice selected is no window, and nothing to confirm`() {
        assertFalse(
            canSubmitInvoicePayment(
                amount = "R$ 50,00",
                paidAmount = "",
                isCrossCurrency = false,
                settles = false,
                date = inCycle,
                window = null,
                outstandingDebt = 100.0,
            )
        )
    }
}
