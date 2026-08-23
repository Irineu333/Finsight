package com.neoutils.finsight.domain.model

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The single owner of what an invoice accepts receiving — read by every surface that
 * offers a payment and by the use case that writes one, so the offer and the permission
 * cannot drift apart.
 *
 * The three predicates answer one question each, and `isPayable` answers a fourth that
 * is not theirs: *who may become `PAID`*.
 */
class InvoicePaymentOfferTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun invoice(status: Invoice.Status) = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 1).plus(1, DateTimeUnit.MONTH),
        dueMonth = YearMonth(2026, 1).plus(2, DateTimeUnit.MONTH),
        status = status,
    )

    @Test
    fun `an invoice still taking spending takes a part of what it owes`() {
        assertTrue(invoice(Invoice.Status.OPEN).acceptsPartialPayment)
        assertTrue(invoice(Invoice.Status.RETROACTIVE).acceptsPartialPayment)
    }

    @Test
    fun `an invoice with a final figure takes no part of it`() {
        assertFalse(invoice(Invoice.Status.CLOSED).acceptsPartialPayment)
        assertFalse(invoice(Invoice.Status.FUTURE).acceptsPartialPayment)
        assertFalse(invoice(Invoice.Status.PAID).acceptsPartialPayment)
    }

    @Test
    fun `only a closed invoice is discharged by paying it`() {
        assertTrue(invoice(Invoice.Status.CLOSED).acceptsFullSettlement)

        assertFalse(invoice(Invoice.Status.OPEN).acceptsFullSettlement)
        assertFalse(invoice(Invoice.Status.RETROACTIVE).acceptsFullSettlement)
        assertFalse(invoice(Invoice.Status.FUTURE).acceptsFullSettlement)
        assertFalse(invoice(Invoice.Status.PAID).acceptsFullSettlement)
    }

    @Test
    fun `the invoices a payment may name are the union of the two, and nothing else`() {
        val offered = Invoice.Status.entries.filter { invoice(it).acceptsPayment }

        assertEquals(
            listOf(Invoice.Status.OPEN, Invoice.Status.CLOSED, Invoice.Status.RETROACTIVE),
            offered,
            "a future cycle has not begun and a paid one is frozen: both are out by construction",
        )
    }

    @Test
    fun `each offered invoice takes exactly one of the two modes`() {
        Invoice.Status.entries.map(::invoice).forEach {
            assertFalse(
                it.acceptsPartialPayment && it.acceptsFullSettlement,
                "the mode is decided by the state, so no state may answer to both",
            )
        }
    }

    /**
     * Narrowing `isPayable` to `CLOSED` would read as the same rule and break the close
     * of a zeroed retroactive invoice, which marks it paid while it is still retroactive.
     */
    @Test
    fun `isPayable is not the offer predicate and still admits a retroactive invoice`() {
        assertTrue(invoice(Invoice.Status.RETROACTIVE).isPayable)
        assertTrue(invoice(Invoice.Status.CLOSED).isPayable)

        assertFalse(invoice(Invoice.Status.OPEN).isPayable)
        assertFalse(invoice(Invoice.Status.FUTURE).isPayable)
        assertFalse(invoice(Invoice.Status.PAID).isPayable)
    }
}
