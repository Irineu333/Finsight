package com.neoutils.finsight.ui.modal.invoicePayment

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Which days a payment may carry — the window the form is bound by, decided by the
 * invoice's state and by nothing else.
 *
 * It is a limit and not a suggestion, so what it says is exactly what the use cases
 * refuse outside of: the cycle while the invoice still takes spending, the stretch from
 * closing to due once it has closed, and never later than today.
 */
class InvoiceSettlementWindowTest {

    // Closes on the 5th, falls due on the 15th of the same month.
    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun invoice(opening: YearMonth, status: Invoice.Status) = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = opening,
        closingMonth = opening.plus(1, DateTimeUnit.MONTH),
        dueMonth = opening.plus(1, DateTimeUnit.MONTH),
        status = status,
    )

    @Test
    fun `an open invoice is paid inside its own cycle, up to today`() {
        val open = invoice(YearMonth(2026, 5), Invoice.Status.OPEN)

        assertEquals(
            LocalDate(2026, 5, 5)..LocalDate(2026, 6, 1),
            open.settlementWindow(today = LocalDate(2026, 6, 1)),
        )
    }

    @Test
    fun `a cycle already over is bounded by its closing, not by today`() {
        val open = invoice(YearMonth(2026, 5), Invoice.Status.OPEN)

        assertEquals(
            LocalDate(2026, 5, 5)..LocalDate(2026, 6, 5),
            open.settlementWindow(today = LocalDate(2026, 8, 1)),
        )
    }

    @Test
    fun `a retroactive invoice is paid wholly in the past`() {
        val retroactive = invoice(YearMonth(2026, 1), Invoice.Status.RETROACTIVE)

        assertEquals(
            LocalDate(2026, 1, 5)..LocalDate(2026, 2, 5),
            retroactive.settlementWindow(today = LocalDate(2026, 6, 1)),
            "regularizing a past cycle is dated inside it, and nowhere after it",
        )
    }

    @Test
    fun `a closed invoice is paid between its closing and its due date`() {
        val closed = invoice(YearMonth(2026, 1), Invoice.Status.CLOSED)

        assertEquals(
            LocalDate(2026, 2, 5)..LocalDate(2026, 2, 15),
            closed.settlementWindow(today = LocalDate(2026, 6, 1)),
        )
    }

    @Test
    fun `today caps a closed invoice that has not fallen due yet`() {
        val closed = invoice(YearMonth(2026, 1), Invoice.Status.CLOSED)

        assertEquals(
            LocalDate(2026, 2, 5)..LocalDate(2026, 2, 8),
            closed.settlementWindow(today = LocalDate(2026, 2, 8)),
        )
    }

    @Test
    fun `an invoice closed before its closing day still has a date to be paid on`() {
        val closed = invoice(YearMonth(2026, 1), Invoice.Status.CLOSED)

        assertEquals(
            LocalDate(2026, 2, 5)..LocalDate(2026, 2, 5),
            closed.settlementWindow(today = LocalDate(2026, 2, 2)),
            "the range stays inhabited; the domain still has the last word on the date",
        )
    }

    @Test
    fun `the day is preserved and the window decides the month`() {
        val window = LocalDate(2026, 1, 5)..LocalDate(2026, 2, 5)

        assertEquals(LocalDate(2026, 1, 20), window.dateOn(day = 20))
        assertEquals(LocalDate(2026, 2, 3), window.dateOn(day = 3))
    }

    @Test
    fun `a day the window admits in neither month is pulled to the nearest edge`() {
        val window = LocalDate(2026, 2, 5)..LocalDate(2026, 2, 15)

        assertEquals(LocalDate(2026, 2, 5), window.dateOn(day = 1))
        assertEquals(LocalDate(2026, 2, 15), window.dateOn(day = 28))
    }
}
