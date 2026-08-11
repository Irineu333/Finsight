package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeEntryRepository
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Available limit is the figure the E2E suite asserts six times over and no unit test
 * watched: `limit − Σ what is still owed`. The rules that make it more than a
 * subtraction are here — which invoices count, what a credit balance does, and what a
 * card with no limit reports.
 */
class CalculateAvailableLimitUseCaseTest {

    private val card = testCard(limit = 1_000.0)

    private fun invoiceOn(month: Int, id: Long, status: Invoice.Status = Invoice.Status.OPEN) =
        testInvoice(id = id, openingMonth = YearMonth(2026, month), status = status, card = card)

    private fun useCase(
        invoices: List<Invoice>,
        owed: Map<Long, Double>,
    ) = CalculateAvailableLimitUseCase(
        invoiceRepository = RecordingInvoiceStore(*invoices.toTypedArray()),
        calculateInvoiceUseCase = CalculateInvoiceUseCase(
            FakeEntryRepository(owedByInvoiceId = owed)
        ),
    )

    @Test
    fun `every unpaid invoice commits limit not just the open one`() = runTest {
        // This is what an installment does: the purchase is spread over invoices, and
        // the whole of it is committed from the moment it is made. The E2E flow asserts
        // the consequence; the rule is here.
        val invoices = listOf(
            invoiceOn(1, id = 1, status = Invoice.Status.CLOSED),
            invoiceOn(2, id = 2),
            invoiceOn(3, id = 3, status = Invoice.Status.FUTURE),
        )
        val owed = mapOf(100L to 320.0, 200L to 320.0, 300L to 320.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(960.0, limit.totalUnpaidAmount)
        assertEquals(40.0, limit.available)
    }

    @Test
    fun `a paid invoice gives its limit back`() = runTest {
        val invoices = listOf(
            invoiceOn(1, id = 1, status = Invoice.Status.PAID),
            invoiceOn(2, id = 2),
        )
        val owed = mapOf(100L to 500.0, 200L to 120.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(120.0, limit.totalUnpaidAmount, "the settled 500.00 is no longer owed")
        assertEquals(880.0, limit.available)
    }

    @Test
    fun `an invoice in credit frees no extra limit`() = runTest {
        // An over-payment leaves an invoice owing less than nothing. Letting it net
        // against its neighbour would report more limit than the card grants, so each
        // invoice is floored at zero on its own — not the sum.
        val invoices = listOf(invoiceOn(1, id = 1), invoiceOn(2, id = 2))
        val owed = mapOf(100L to -200.0, 200L to 300.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(300.0, limit.totalUnpaidAmount)
        assertEquals(700.0, limit.available)
    }

    @Test
    fun `owing more than the limit reports no limit left never a negative one`() = runTest {
        val invoices = listOf(invoiceOn(1, id = 1))
        val owed = mapOf(100L to 1_500.0)

        val limit = useCase(invoices, owed)(card)

        assertEquals(1_500.0, limit.totalUnpaidAmount, "what is owed is reported as it is")
        assertEquals(0.0, limit.available)
        assertEquals(1.0, limit.usage, "and usage stops at full, so no bar overflows")
    }

    @Test
    fun `a card with no limit reports no usage instead of dividing by zero`() = runTest {
        val noLimit = testCard(limit = 0.0)
        val invoices = listOf(invoiceOn(1, id = 1, status = Invoice.Status.OPEN))
        val owed = mapOf(100L to 250.0)

        val limit = CalculateAvailableLimitUseCase(
            invoiceRepository = RecordingInvoiceStore(*invoices.toTypedArray()),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(owed)),
        )(noLimit)

        assertEquals(250.0, limit.totalUnpaidAmount)
        assertEquals(0.0, limit.available)
        assertEquals(0.0, limit.usage)
    }

    @Test
    fun `a card with nothing on it has all of its limit`() = runTest {
        val limit = useCase(invoices = emptyList(), owed = emptyMap())(card)

        assertEquals(0.0, limit.totalUnpaidAmount)
        assertEquals(1_000.0, limit.available)
        assertEquals(0.0, limit.usage)
    }
}
