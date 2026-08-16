package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Marking an invoice paid is a window, not a flag: it opens when the invoice closes,
 * shuts on its due date, and never runs ahead of today. Three dates, and every one of
 * them a refusal the UI relies on but no test watched.
 *
 * The clock is injected, so "in the future" is a fact of the test rather than of the
 * day it runs on.
 */
class PayInvoiceUseCaseTest {

    // Closing day 5, due day 15: the January invoice closes 05/02 and falls due 15/02.
    private val invoice = testInvoice(
        openingMonth = YearMonth(2026, 1),
        status = Invoice.Status.CLOSED,
    )

    private fun useCase(
        today: LocalDate = LocalDate(2026, 2, 20),
        store: RecordingInvoiceStore = RecordingInvoiceStore(invoice),
    ) = PayInvoiceUseCaseImpl(store, StoppedClock(today))

    @Test
    fun `paying between closing and due settles it, and records when`() = runTest {
        val store = RecordingInvoiceStore(invoice)

        val paid = useCase(store = store)(invoice.id, LocalDate(2026, 2, 10)).getOrNull()

        assertEquals(Invoice.Status.PAID, paid?.status)
        assertEquals(LocalDate(2026, 2, 10), paid?.paidAt)
        assertEquals(Invoice.Status.PAID, store.byId(invoice.id)?.status, "and it is persisted")
    }

    @Test
    fun `the closing date itself is inside the window`() = runTest {
        val paid = useCase()(invoice.id, invoice.closingDate).getOrNull()

        assertEquals(Invoice.Status.PAID, paid?.status)
    }

    @Test
    fun `the due date itself is inside the window`() = runTest {
        val paid = useCase()(invoice.id, invoice.dueDate).getOrNull()

        assertEquals(Invoice.Status.PAID, paid?.status)
    }

    @Test
    fun `an open invoice is not payable`() = runTest {
        val open = testInvoice(status = Invoice.Status.OPEN)
        val store = RecordingInvoiceStore(open)

        val result = useCase(store = store)(open.id, LocalDate(2026, 2, 10))

        assertEquals(InvoiceError.CannotPayOpenInvoice, result.leftOrNull()?.error)
        assertTrue(store.updates.isEmpty(), "a refusal writes nothing")
    }

    @Test
    fun `paying before it closed is refused`() = runTest {
        val result = useCase()(invoice.id, LocalDate(2026, 2, 4))

        assertEquals(InvoiceError.PaymentDateBeforeClosing, result.leftOrNull()?.error)
    }

    @Test
    fun `paying after it fell due is refused`() = runTest {
        val result = useCase()(invoice.id, LocalDate(2026, 2, 16))

        assertEquals(InvoiceError.PaymentDateAfterDue, result.leftOrNull()?.error)
    }

    @Test
    fun `paying on a day that has not happened yet is refused`() = runTest {
        // Inside the invoice's window, but the app's clock has not reached it. Without
        // the injected clock this case could only be written as "tomorrow", and would
        // read differently depending on the day the suite runs.
        val result = useCase(today = LocalDate(2026, 2, 8))(invoice.id, LocalDate(2026, 2, 12))

        assertEquals(InvoiceError.PaymentDateInFuture, result.leftOrNull()?.error)
    }

    @Test
    fun `an invoice that is not there is not paid`() = runTest {
        val result = useCase(store = RecordingInvoiceStore())(99, LocalDate(2026, 2, 10))

        val error = assertIs<InvoiceException>(result.leftOrNull())
        assertEquals(InvoiceError.NotFound, error.error)
    }
}
