package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `InvoiceWindow` is the single owner of two rules a due month alone cannot answer: which
 * days a purchase must carry to land on a given invoice, and which date inside that span
 * falls on a wanted day.
 */
class InvoiceWindowTest {

    private fun card(closingDay: Int, dueDay: Int) = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = closingDay,
        dueDay = dueDay,
        accountId = 10,
    )

    private fun invoice(
        card: CreditCard,
        openingMonth: YearMonth,
        closingMonth: YearMonth,
        dueMonth: YearMonth,
    ) = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = openingMonth,
        closingMonth = closingMonth,
        dueMonth = dueMonth,
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `due day after the closing day keeps the cycle in the due month`() {
        val window = card(closingDay = 10, dueDay = 20)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(YearMonth(2026, 2), window.openingMonth)
        assertEquals(YearMonth(2026, 3), window.closingMonth)
        assertEquals(LocalDate(2026, 2, 10), window.openingDate)
        assertEquals(LocalDate(2026, 3, 10), window.closingDate)
    }

    @Test
    fun `due day before the closing day pulls the cycle one month back`() {
        val window = card(closingDay = 25, dueDay = 5)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(YearMonth(2026, 2), window.closingMonth)
        assertEquals(LocalDate(2026, 1, 25), window.openingDate)
        assertEquals(LocalDate(2026, 2, 25), window.closingDate)
    }

    @Test
    fun `an existing invoice answers for the months it recorded`() {
        val card = card(closingDay = 10, dueDay = 20)
        val invoice = invoice(
            card = card,
            openingMonth = YearMonth(2026, 2),
            closingMonth = YearMonth(2026, 3),
            dueMonth = YearMonth(2026, 3),
        )

        assertEquals(YearMonth(2026, 2), invoice.window.openingMonth)
        assertEquals(YearMonth(2026, 3), invoice.window.closingMonth)
        assertEquals(invoice.window.openingDate, invoice.openingDate)
        assertEquals(invoice.window.closingDate, invoice.closingDate)
    }

    @Test
    fun `a month with no invoice has the window its invoice would be created with`() {
        val card = card(closingDay = 10, dueDay = 20)
        val dueMonth = YearMonth(2026, 3)

        val derived = card.invoiceWindowFor(dueMonth)
        val recorded = invoice(
            card = card,
            openingMonth = derived.openingMonth,
            closingMonth = derived.closingMonth,
            dueMonth = dueMonth,
        ).window

        assertEquals(recorded, derived)
    }

    @Test
    fun `a day past the closing day falls in the opening month`() {
        val window = card(closingDay = 10, dueDay = 20)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(LocalDate(2026, 2, 15), window.dateOn(15))
    }

    @Test
    fun `a day before the closing day falls in the closing month`() {
        val window = card(closingDay = 10, dueDay = 20)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(LocalDate(2026, 3, 5), window.dateOn(5))
    }

    @Test
    fun `the closing day itself is the opening date - the inclusive edge`() {
        val window = card(closingDay = 10, dueDay = 20)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(LocalDate(2026, 2, 10), window.dateOn(10))
        assertEquals(window.openingDate, window.dateOn(10))
    }

    @Test
    fun `projecting a date already inside the window does not move it`() {
        val window = card(closingDay = 10, dueDay = 20)
            .invoiceWindowFor(YearMonth(2026, 3))
        val inside = LocalDate(2026, 3, 5)

        assertTrue(inside in window)
        assertEquals(inside, window.dateOn(inside.day))
    }

    @Test
    fun `a day in neither candidate month is pulled back into the window`() {
        // Closing on the 31st: the window is 31/jan–28/feb, and the 30th exists in neither
        // segment. The projection stays total by landing on the inclusive edge.
        val window = card(closingDay = 31, dueDay = 10)
            .invoiceWindowFor(YearMonth(2026, 3))

        assertEquals(LocalDate(2026, 1, 31), window.openingDate)
        assertEquals(LocalDate(2026, 2, 28), window.closingDate)
        assertTrue(window.dateOn(30) in window)
        assertEquals(LocalDate(2026, 1, 31), window.dateOn(30))
    }
}
