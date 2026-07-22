package com.neoutils.finsight.domain.ledger

import com.neoutils.finsight.domain.error.InvoiceLockedException
import com.neoutils.finsight.domain.error.LedgerError
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * The invoice-status rule itself, now that it is owned here rather than by the
 * ledger. That the rule is *asked* on every write path is the other half, and lives
 * with the write boundary in transactions.
 */
class InvoiceWriteGuardTest {

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 10, dueDay = 20, accountId = 10)

    private fun invoice(status: Invoice.Status) = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 5,
        openingMonth = YearMonth(2026, 2),
        closingMonth = YearMonth(2026, 3),
        dueMonth = YearMonth(2026, 3),
        status = status,
    )

    private fun guard(status: Invoice.Status) = InvoiceWriteGuard(SingleInvoiceRepository(invoice(status)))

    private fun write(settlesALiability: Boolean = false) =
        LedgerWrite(dimensionIds = setOf(5), settlesALiability = settlesALiability)

    @Test
    fun `an open invoice accepts anything`() = runTest {
        guard(Invoice.Status.OPEN).ensureAccepts(write())
        guard(Invoice.Status.OPEN).ensureAccepts(write(settlesALiability = true))
    }

    @Test
    fun `a closed invoice refuses new spending`() = runTest {
        val error = assertFailsWith<InvoiceLockedException> {
            guard(Invoice.Status.CLOSED).ensureAccepts(write())
        }
        assertEquals(LedgerError.ClosedInvoice, error.error)
    }

    @Test
    fun `a closed invoice accepts the payment that settles it`() = runTest {
        // Which is the whole point of closing — and why `isClosedToNewExpenses`,
        // which fuses CLOSED and PAID, is not the predicate here.
        guard(Invoice.Status.CLOSED).ensureAccepts(write(settlesALiability = true))
    }

    @Test
    fun `a paid invoice refuses everything, settlement included`() = runTest {
        assertFailsWith<InvoiceLockedException> {
            guard(Invoice.Status.PAID).ensureAccepts(write())
        }
        val error = assertFailsWith<InvoiceLockedException> {
            guard(Invoice.Status.PAID).ensureAccepts(write(settlesALiability = true))
        }
        assertEquals(LedgerError.PaidInvoice, error.error)
    }

    @Test
    fun `a write touching no invoice dimension is none of this guard's business`() = runTest {
        // The ledger asks every owner about every write; an owner that recognises
        // none of the dimensions has nothing to say.
        guard(Invoice.Status.PAID).ensureAccepts(LedgerWrite(dimensionIds = setOf(999), settlesALiability = false))
    }
}

private class SingleInvoiceRepository(private val invoice: Invoice) : IInvoiceRepository {
    override suspend fun getAllInvoices(): List<Invoice> = listOf(invoice)
    override fun observeAllInvoices(): Flow<List<Invoice>> = flowOf(listOf(invoice))
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = flowOf(listOf(invoice))
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = flowOf(invoice)
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = flowOf(invoice)
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = flowOf(listOf(invoice))
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = flowOf(invoice)
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = flowOf(listOf(invoice))
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = listOf(invoice)
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = listOf(invoice)
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = invoice
    override suspend fun getInvoiceById(id: Long): Invoice? = invoice.takeIf { it.id == id }
    override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}
