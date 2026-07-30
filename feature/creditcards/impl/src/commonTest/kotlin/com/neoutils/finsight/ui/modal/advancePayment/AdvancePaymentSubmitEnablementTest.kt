package com.neoutils.finsight.ui.modal.advancePayment

import com.neoutils.finsight.util.dayMonthYear
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The submit button of the advance payment covering the **second** field (design D26),
 * and the ceiling staying where it belongs.
 *
 * `amount <= outstandingDebt` holds over the **card's** side, where both figures are
 * denominated the same way; what leaves the account carries no ceiling at all, because
 * one there would be a limit expressed in the wrong currency.
 */
class AdvancePaymentSubmitEnablementTest {

    private val opening = LocalDate(2026, 7, 1)
    private val closing = LocalDate(2026, 7, 20)
    private val date = dayMonthYear.format(LocalDate(2026, 7, 5))

    @Test
    fun `a cross-currency advance waits for what leaves the account`() {
        assertFalse(
            isValidAdvancePayment(
                amount = "US$ 50,00",
                paidAmount = "",
                isCrossCurrency = true,
                date = date,
                minDate = opening,
                maxDate = closing,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `a second field of zero is no more submittable than an empty one`() {
        assertFalse(
            isValidAdvancePayment(
                amount = "US$ 50,00",
                paidAmount = "R$ 0,00",
                isCrossCurrency = true,
                date = date,
                minDate = opening,
                maxDate = closing,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `the ceiling holds over the card's side and not over the account's`() {
        // 275 reais leaving to settle 50 dollars of a 100-dollar invoice: the account
        // side is far above the invoice's number and that is not a comparison at all.
        assertTrue(
            isValidAdvancePayment(
                amount = "US$ 50,00",
                paidAmount = "R$ 275,00",
                isCrossCurrency = true,
                date = date,
                minDate = opening,
                maxDate = closing,
                outstandingDebt = 100.0,
            )
        )

        assertFalse(
            isValidAdvancePayment(
                amount = "US$ 150,00",
                paidAmount = "R$ 825,00",
                isCrossCurrency = true,
                date = date,
                minDate = opening,
                maxDate = closing,
                outstandingDebt = 100.0,
            )
        )
    }

    @Test
    fun `a same-currency advance never waits for a second field`() {
        assertTrue(
            isValidAdvancePayment(
                amount = "R$ 50,00",
                paidAmount = "",
                isCrossCurrency = false,
                date = date,
                minDate = opening,
                maxDate = closing,
                outstandingDebt = 100.0,
            )
        )
    }
}
