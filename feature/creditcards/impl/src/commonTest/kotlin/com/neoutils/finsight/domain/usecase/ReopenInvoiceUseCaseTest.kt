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
 * always went straight to PAID. Closing overwrites RETROACTIVE with CLOSED and nothing
 * persists the origin, so reopen cannot tell a formerly-retroactive invoice from any
 * other closed one and treats them alike (accepted decision): it reopens as OPEN and
 * demotes the current cycle to FUTURE. The one invariant reopen must never break is
 * two OPEN invoices on the same card.
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
    fun `reopening a formerly-retroactive closed invoice reopens as OPEN with a single OPEN left`() = runTest {
        // A retroactive invoice for a past cycle, closed with a balance: status is now
        // CLOSED, indistinguishable from any other closed invoice. Its closingMonth
        // aligns with the current cycle's openingMonth.
        val pastCycle = invoice(1, YearMonth(2026, 1), Invoice.Status.CLOSED)
        val current = invoice(2, YearMonth(2026, 2), Invoice.Status.OPEN)
        val repo = FakeInvoiceStore(pastCycle, current)

        val reopened = ReopenInvoiceUseCase(repo)(pastCycle.id).getOrNull()

        assertEquals(Invoice.Status.OPEN, reopened?.status)
        assertNull(reopened?.closedAt)
        // Accepted behavior: the current cycle steps back to FUTURE, so the card is
        // left with exactly one OPEN invoice — never two.
        assertEquals(Invoice.Status.FUTURE, repo.byId(current.id)?.status)
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
