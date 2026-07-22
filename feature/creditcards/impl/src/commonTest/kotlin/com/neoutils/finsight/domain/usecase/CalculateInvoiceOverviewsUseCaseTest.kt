package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.DimensionFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [CalculateInvoiceOverviewsUseCase] over the ledger (task 4.11):
 * expense/advancePayment/adjustment come from [IEntryRepository.dimensionFlows] and the
 * owed total from [IEntryRepository.dimensionOwed]; the use case only selects the
 * invoices closing in the month and aggregates. The numbers match the legacy leg-based
 * form.
 */
class CalculateInvoiceOverviewsUseCaseTest {

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)

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
            flows = mapOf(1L to DimensionFlows(expense = 100.0, advancePayment = 30.0, adjustment = 10.0)),
            owed = mapOf(1L to 60.0),
        )
        val useCase = CalculateInvoiceOverviewsUseCase(entryRepository)

        val stats = useCase(invoices = listOf(march, april), forYearMonth = YearMonth(2026, 3))
        val overview = stats.invoiceOverviews.single()

        assertEquals(100.0, overview.expense)
        assertEquals(30.0, overview.advancePayment)
        assertEquals(10.0, overview.adjustment)
        assertEquals(60.0, overview.total)
        assertEquals(100.0, stats.creditCardOverview.expense)
        assertEquals(60.0, stats.creditCardOverview.total)
    }
}

private class FakeInvoiceOverviewEntryRepository(
    private val flows: Map<Long, DimensionFlows>,
    private val owed: Map<Long, Double>,
) : IEntryRepository {
    override suspend fun dimensionFlows(dimensionId: Long): DimensionFlows = flows.getValue(dimensionId)
    override suspend fun dimensionOwed(dimensionId: Long): Double = owed.getValue(dimensionId)
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(nominalType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScope(nominalType: AccountType, scopeDimensionIds: List<Long>): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun reportStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ReportStats = throw NotImplementedError()
}
