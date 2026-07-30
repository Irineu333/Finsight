package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.test.StubEntryRepository
import com.neoutils.finsight.test.brl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Characterizes [CalculateInvoiceOverviewsUseCase] over the ledger (task 4.11):
 * expense/advancePayment/adjustment come from [IEntryRepository.dimensionFlows] and the
 * owed total from [IEntryRepository.dimensionOwed]; the use case only selects the
 * invoices closing in the month and aggregates. The numbers match the legacy leg-based
 * form.
 */
class CalculateInvoiceOverviewsUseCaseTest {

    private val card = CreditCard(currency = "BRL", id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)

    private fun invoice(id: Long, closing: Int) = Invoice(
        id = id, creditCard = card, dimensionId = id,
        openingMonth = YearMonth(2026, closing - 1), closingMonth = YearMonth(2026, closing), dueMonth = YearMonth(2026, closing + 1),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `invoice overview reads the ledger flows and owed of the closing invoices`() = runTest {
        val march = invoice(id = 1, closing = 3)
        val april = invoice(id = 2, closing = 4)
        // Ledger reads for invoice 1 (march): expense 100, advance payment 30, adjustment 10,
        // owed = +expense − income − adjustment = 60. Invoice 2 (april) does not close in march.
        val entryRepository = FakeInvoiceOverviewEntryRepository(
            flows = mapOf(1L to DimensionFlows(expense = brl(100.0), advancePayment = brl(30.0), adjustment = brl(10.0))),
            owed = mapOf(1L to 60.0),
        )
        val useCase = CalculateInvoiceOverviewsUseCase(entryRepository)

        val stats = useCase(invoices = listOf(march, april), forYearMonth = YearMonth(2026, 3))
        val overview = stats.invoiceOverviews.single()

        assertEquals(100.0, overview.expense)
        assertEquals(30.0, overview.advancePayment)
        assertEquals(10.0, overview.adjustment)
        assertEquals(60.0, overview.total)
        assertEquals(100.0, stats.creditCardOverview.expense.soleAmount)
        assertEquals(60.0, stats.creditCardOverview.total.soleAmount)
    }
}

private class FakeInvoiceOverviewEntryRepository(
    private val flows: Map<Long, DimensionFlows>,
    private val owed: Map<Long, Double>,
) : StubEntryRepository() {
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = flows.getValue(dimensionId)
    override suspend fun dimensionOwed(dimensionId: Long) = brl(owed.getValue(dimensionId))
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
}
