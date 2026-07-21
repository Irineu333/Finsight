package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.InvoiceError
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * A retroactive invoice with a balance now reaches CLOSED (CloseInvoiceUseCase), so
 * the UI offers to reopen it — a path unreachable on main, where a retroactive invoice
 * always went straight to PAID. Reopening it as OPEN would leave two OPEN invoices on
 * the card and erase RETROACTIVE. Reopening must instead restore RETROACTIVE, and must
 * never produce a second OPEN by any path.
 */
class ReopenInvoiceUseCaseTest {

    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )

    private fun invoice(
        id: Long,
        openingMonth: YearMonth,
        status: Invoice.Status,
    ) = Invoice(
        id = id,
        creditCard = card,
        openingMonth = openingMonth,
        closingMonth = openingMonth.plus(1, DateTimeUnit.MONTH),
        dueMonth = openingMonth.plus(2, DateTimeUnit.MONTH),
        status = status,
        closedAt = LocalDate(2026, 2, 5).takeIf { status == Invoice.Status.CLOSED },
    )

    @Test
    fun `reopening a retroactive invoice restores RETROACTIVE and never creates a second OPEN`() = runTest {
        val retroactive = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
            .copy(status = Invoice.Status.RETROACTIVE, closedAt = LocalDate(2026, 2, 5))
        val current = invoice(2, YearMonth(2026, 3), Invoice.Status.OPEN)
        val repo = FakeInvoiceStore(retroactive, current)

        val result = ReopenInvoiceUseCase(repo)(retroactive.id)

        val reopened = result.getOrNull()
        assertEquals(Invoice.Status.RETROACTIVE, reopened?.status)
        assertNull(reopened?.closedAt)
        // The current cycle is untouched: still exactly one OPEN on the card.
        assertEquals(
            listOf(Invoice.Status.RETROACTIVE, Invoice.Status.OPEN),
            repo.all().map { it.status },
        )
        assertEquals(1, repo.all().count { it.status == Invoice.Status.OPEN })
    }

    @Test
    fun `reopening the latest closed invoice demotes its open successor`() = runTest {
        val closed = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val successor = invoice(2, YearMonth(2026, 2), Invoice.Status.OPEN)
        val repo = FakeInvoiceStore(closed, successor)

        val reopened = ReopenInvoiceUseCase(repo)(closed.id).getOrNull()

        assertEquals(Invoice.Status.OPEN, reopened?.status)
        assertNull(reopened?.closedAt)
        assertEquals(Invoice.Status.FUTURE, repo.byId(successor.id)?.status)
        assertEquals(1, repo.all().count { it.status == Invoice.Status.OPEN })
    }

    @Test
    fun `reopening a mid-chain closed invoice is refused rather than creating a second OPEN`() = runTest {
        val midChain = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val nextClosed = invoice(2, YearMonth(2026, 2), Invoice.Status.CLOSED)
        val current = invoice(3, YearMonth(2026, 3), Invoice.Status.OPEN)
        val repo = FakeInvoiceStore(midChain, nextClosed, current)

        val result = ReopenInvoiceUseCase(repo)(midChain.id)

        assertTrue(result.isLeft())
        assertEquals(InvoiceError.CannotReopenInvoice, result.leftOrNull()?.error)
        // Nothing moved.
        assertEquals(
            listOf(Invoice.Status.CLOSED, Invoice.Status.CLOSED, Invoice.Status.OPEN),
            repo.all().map { it.status },
        )
    }

    @Test
    fun `reopening a paid invoice is refused`() = runTest {
        val paid = invoice(1, YearMonth(2026, 1), Invoice.Status.PAID)
        val repo = FakeInvoiceStore(paid)

        val result = ReopenInvoiceUseCase(repo)(paid.id)

        assertEquals(InvoiceError.CannotReopenPaidInvoice, result.leftOrNull()?.error)
    }

    @Test
    fun `reopening an open invoice is refused`() = runTest {
        val open = invoice(1, YearMonth(2026, 1), Invoice.Status.OPEN)
        val repo = FakeInvoiceStore(open)

        val result = ReopenInvoiceUseCase(repo)(open.id)

        assertEquals(InvoiceError.AlreadyOpen, result.leftOrNull()?.error)
    }
}

private class FakeInvoiceStore(vararg invoices: Invoice) : IInvoiceRepository {

    private val store = invoices.associateBy { it.id }.toMutableMap()

    fun all(): List<Invoice> = store.values.sortedBy { it.id }
    fun byId(id: Long): Invoice? = store[id]

    override suspend fun getInvoiceById(id: Long): Invoice? = store[id]

    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> =
        store.values.filter { it.creditCard.id == creditCardId }

    override suspend fun update(invoice: Invoice) {
        store[invoice.id] = invoice
    }

    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}
