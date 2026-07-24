package com.neoutils.finsight.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * `isReopenable` is the single owner of the "only the latest closed invoice can be
 * reopened" rule — read by `ReopenInvoiceUseCase` (enforcement) and by the screens
 * (whether to offer the button). Reopenable iff the successor at
 * `openingMonth == this.closingMonth` is the current OPEN one.
 */
class InvoiceReopenableTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun invoice(id: Long, opening: YearMonth, status: Invoice.Status) = Invoice(
        id = id,
        creditCard = card,
        openingMonth = opening,
        closingMonth = opening.plus(1, DateTimeUnit.MONTH),
        dueMonth = opening.plus(2, DateTimeUnit.MONTH),
        status = status,
    )

    @Test
    fun `latest closed with an open successor is reopenable`() {
        val closed = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val open = invoice(2, YearMonth(2026, 2), Invoice.Status.OPEN)

        assertTrue(closed.isReopenable(listOf(closed, open)))
    }

    @Test
    fun `mid-chain closed with a closed successor is not reopenable`() {
        val closed = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val nextClosed = invoice(2, YearMonth(2026, 2), Invoice.Status.CLOSED)
        val open = invoice(3, YearMonth(2026, 3), Invoice.Status.OPEN)

        assertFalse(closed.isReopenable(listOf(closed, nextClosed, open)))
    }

    @Test
    fun `closed with a paid successor is not reopenable`() {
        val closed = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val paid = invoice(2, YearMonth(2026, 2), Invoice.Status.PAID)

        assertFalse(closed.isReopenable(listOf(closed, paid)))
    }

    @Test
    fun `closed with no successor is not reopenable`() {
        val closed = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val unrelatedOpen = invoice(2, YearMonth(2026, 6), Invoice.Status.OPEN)

        assertFalse(closed.isReopenable(listOf(closed, unrelatedOpen)))
    }

    @Test
    fun `open and paid invoices are never reopenable`() {
        val open = invoice(1, YearMonth(2026, 1), Invoice.Status.OPEN)
        val paid = invoice(2, YearMonth(2026, 1), Invoice.Status.PAID)
        val later = invoice(3, YearMonth(2026, 2), Invoice.Status.OPEN)

        assertFalse(open.isReopenable(listOf(open, later)))
        assertFalse(paid.isReopenable(listOf(paid, later)))
    }
}
