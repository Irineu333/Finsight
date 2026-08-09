package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Closing is the one command that decides what happens next: it settles an invoice
 * that owes nothing, opens the following cycle for one that does, and does neither
 * for a retroactive one. The E2E suite exercises the happy path once; the branches
 * that only differ by a balance or a status live here.
 *
 * The invariant behind all of it: a card never has two OPEN invoices.
 */
class CloseInvoiceUseCaseTest {

    private val card = testCard()

    // Closing day 5, due day 15: the January invoice closes in February.
    private val january = YearMonth(2026, 1)
    private val closingDay = LocalDate(2026, 2, 5)

    private fun useCase(
        store: RecordingInvoiceStore,
        owed: Map<Long, Double>,
        today: LocalDate = LocalDate(2026, 2, 20),
    ): CloseInvoiceUseCase {
        val clock = StoppedClock(today)
        val calculate = CalculateInvoiceUseCase(FakeEntryRepository(owed))
        return CloseInvoiceUseCase(
            invoiceRepository = store,
            calculateInvoiceUseCase = calculate,
            payInvoiceUseCase = PayInvoiceUseCase(store, clock),
            openInvoiceUseCase = OpenInvoiceUseCase(store, SingleCardRepository(card), clock),
        )
    }

    @Test
    fun `closing an invoice that owes something opens the next cycle`() = runTest {
        val invoice = testInvoice(openingMonth = january, card = card)
        val store = RecordingInvoiceStore(invoice)

        val closed = useCase(store, owed = mapOf(100L to 120.0))(invoice.id, closingDay).getOrNull()

        assertEquals(Invoice.Status.CLOSED, closed?.status)
        assertEquals(closingDay, closed?.closedAt)
        // The cycle the closing month starts is now the card's open one — and the only one.
        val open = store.getAllInvoices().filter { it.status.isOpen }
        assertEquals(1, open.size)
        assertEquals(invoice.closingMonth, open.single().openingMonth)
    }

    @Test
    fun `closing an invoice that owes nothing settles it on the spot`() = runTest {
        // Nothing is left to pay, so asking the user to pay zero would be theatre.
        val invoice = testInvoice(openingMonth = january, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to 0.0))(invoice.id, closingDay).getOrNull()

        assertEquals(Invoice.Status.PAID, result?.status)
        assertEquals(closingDay, result?.paidAt)
        assertEquals(1, store.getAllInvoices().count { it.status.isOpen }, "and the next cycle still opened")
    }

    @Test
    fun `an invoice in credit is not closed at all`() = runTest {
        // A negative balance means more was paid into it than was spent. Closing would
        // freeze a credit the user cannot then spend against.
        val invoice = testInvoice(openingMonth = january, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to -50.0))(invoice.id, closingDay)

        assertEquals(InvoiceError.NegativeBalance, result.leftOrNull()?.error)
        assertTrue(store.updates.isEmpty())
        assertTrue(store.inserts.isEmpty(), "and no next cycle was opened either")
    }

    @Test
    fun `a retroactive invoice with a balance closes without opening anything`() = runTest {
        // It belongs to a past cycle and the current one is already open; opening
        // another would leave the card with two, which every invoice lookup assumes
        // cannot happen.
        val past = testInvoice(id = 1, openingMonth = january, status = Invoice.Status.RETROACTIVE, card = card)
        val current = testInvoice(id = 2, openingMonth = YearMonth(2026, 2), card = card)
        val store = RecordingInvoiceStore(past, current)

        val closed = useCase(store, owed = mapOf(100L to 300.0))(past.id, closingDay).getOrNull()

        assertEquals(Invoice.Status.CLOSED, closed?.status)
        assertTrue(store.inserts.isEmpty())
        assertEquals(1, store.getAllInvoices().count { it.status.isOpen })
    }

    @Test
    fun `a retroactive invoice owing nothing is settled not merely closed`() = runTest {
        val past = testInvoice(id = 1, openingMonth = january, status = Invoice.Status.RETROACTIVE, card = card)
        val current = testInvoice(id = 2, openingMonth = YearMonth(2026, 2), card = card)
        val store = RecordingInvoiceStore(past, current)

        val result = useCase(store, owed = mapOf(100L to 0.0))(past.id, closingDay).getOrNull()

        assertEquals(Invoice.Status.PAID, result?.status)
        assertNotNull(result?.paidAt)
        assertTrue(store.inserts.isEmpty())
    }

    @Test
    fun `closing outside the closing month is refused`() = runTest {
        val invoice = testInvoice(openingMonth = january, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to 120.0))(invoice.id, LocalDate(2026, 3, 5))

        assertEquals(InvoiceError.CannotCloseOutsideClosingMonth, result.leftOrNull()?.error)
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `a paid invoice cannot be closed again`() = runTest {
        val invoice = testInvoice(openingMonth = january, status = Invoice.Status.PAID, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to 0.0))(invoice.id, closingDay)

        assertEquals(InvoiceError.CannotClosePaidInvoice, result.leftOrNull()?.error)
    }

    @Test
    fun `a closed invoice cannot be closed again`() = runTest {
        val invoice = testInvoice(openingMonth = january, status = Invoice.Status.CLOSED, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to 120.0))(invoice.id, closingDay)

        assertEquals(InvoiceError.AlreadyClosed, result.leftOrNull()?.error)
    }

    @Test
    fun `a future invoice is not closable though the status checks above admit it`() = runTest {
        // The two `!=` guards let FUTURE through; `isClosable` is what stops it. The
        // comment in the use case says so, and this is the case that holds it there.
        val invoice = testInvoice(openingMonth = january, status = Invoice.Status.FUTURE, card = card)
        val store = RecordingInvoiceStore(invoice)

        val result = useCase(store, owed = mapOf(100L to 120.0))(invoice.id, closingDay)

        assertEquals(InvoiceError.AlreadyClosed, result.leftOrNull()?.error)
        assertTrue(store.updates.isEmpty())
    }

    @Test
    fun `an invoice that is not there is not closed`() = runTest {
        val store = RecordingInvoiceStore()

        val result = useCase(store, owed = emptyMap())(99, closingDay)

        assertEquals(InvoiceError.NotFound, result.leftOrNull()?.error)
        assertNull(store.byId(99))
    }
}
