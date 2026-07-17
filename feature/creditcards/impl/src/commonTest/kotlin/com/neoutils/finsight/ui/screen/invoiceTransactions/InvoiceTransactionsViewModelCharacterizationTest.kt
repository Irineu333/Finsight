@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the per-invoice sums of [InvoiceTransactionsViewModel] (sites
 * :102,106,110): expense/advancePayment/adjustment of the card legs, and the owed
 * total read from the ledger (`invoiceOwed`). Task 4.11 flips the sums to the ledger;
 * the numbers must survive.
 */
class InvoiceTransactionsViewModelCharacterizationTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1, creditCard = card,
        openingMonth = YearMonth(2026, 2), closingMonth = YearMonth(2026, 3), dueMonth = YearMonth(2026, 4),
        status = Invoice.Status.OPEN,
    )

    private fun cardLeg(type: Transaction.Type, amount: Double) = Transaction(
        type = type, amount = amount, title = null, date = LocalDate(2026, 3, 10), creditCard = card, invoice = invoice,
    )

    private fun op(id: Long, leg: Transaction) = Operation(id = id, title = null, date = leg.date, transactions = listOf(leg))

    @Test
    fun `invoice summary characterizes the card leg sums and owed total`() = runTest(dispatcher) {
        val operations = listOf(
            op(1, cardLeg(Transaction.Type.EXPENSE, 60.0)),
            op(2, cardLeg(Transaction.Type.EXPENSE, 40.0)),
            op(3, cardLeg(Transaction.Type.ADJUSTMENT, 10.0)),
            op(4, cardLeg(Transaction.Type.INCOME, 30.0)), // advance payment
        )
        val vm = InvoiceTransactionsViewModel(
            creditCardId = 1,
            creditCardRepository = FakeCreditCardRepository(card),
            invoiceRepository = FakeInvoiceRepository(listOf(invoice)),
            operationRepository = FakeOperationRepository(operations),
            categoryRepository = FakeCategoryRepository(),
            entryRepository = FakeEntryRepository(owedByInvoiceId = mapOf(1L to 70.0)),
        )

        vm.uiState.test {
            var summary = awaitItem().invoices.firstOrNull()
            while (summary == null) summary = awaitItem().invoices.firstOrNull()
            assertEquals(100.0, summary.expense)
            assertEquals(30.0, summary.advancePayment)
            assertEquals(10.0, summary.adjustment)
            assertEquals(70.0, summary.total, "owed comes from the ledger's invoiceOwed")
            cancelAndIgnoreRemainingEvents()
        }
    }
}

private class FakeCreditCardRepository(private val card: CreditCard) : ICreditCardRepository {
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = MutableStateFlow(card)
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = throw NotImplementedError()
    override suspend fun getAllCreditCards(): List<CreditCard> = throw NotImplementedError()
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = card
    override suspend fun insert(creditCard: CreditCard): Long = throw NotImplementedError()
    override suspend fun update(creditCard: CreditCard) = throw NotImplementedError()
    override suspend fun delete(creditCard: CreditCard) = throw NotImplementedError()
}

private class FakeInvoiceRepository(private val invoices: List<Invoice>) : IInvoiceRepository {
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = MutableStateFlow(invoices)
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = invoices
    override suspend fun getInvoiceById(id: Long): Invoice? = invoices.firstOrNull { it.id == id }
    override fun observeAllInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = throw NotImplementedError()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = throw NotImplementedError()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = throw NotImplementedError()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = throw NotImplementedError()
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = throw NotImplementedError()
    override suspend fun insert(invoice: Invoice): Long = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class FakeOperationRepository(private val operations: List<Operation>) : IOperationRepository {
    override fun observeOperationsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Operation>> = MutableStateFlow(operations)
    override fun observeAllOperations(): Flow<List<Operation>> = throw NotImplementedError()
    override fun observeOperationById(id: Long): Flow<Operation?> = throw NotImplementedError()
    override suspend fun getAllOperations(): List<Operation> = throw NotImplementedError()
    override suspend fun getOperationById(id: Long): Operation? = throw NotImplementedError()
    override suspend fun createOperation(title: String?, date: LocalDate, categoryId: Long?, recurringId: Long?, recurringCycle: Int?, installmentId: Long?, installmentNumber: Int?, transactions: List<Transaction>): Operation = throw NotImplementedError()
    override suspend fun updateOperation(id: Long, transaction: Transaction) = throw NotImplementedError()
    override suspend fun deleteOperationById(id: Long) = throw NotImplementedError()
    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
}

private class FakeCategoryRepository : ICategoryRepository {
    override fun observeAllCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
    override fun observeCategoryById(id: Long): Flow<Category?> = throw NotImplementedError()
    override suspend fun getAllCategories(): List<Category> = throw NotImplementedError()
    override fun observeCategoriesByType(type: Category.Type): Flow<List<Category>> = throw NotImplementedError()
    override suspend fun getCategoryById(id: Long): Category? = throw NotImplementedError()
    override suspend fun insert(category: Category) = throw NotImplementedError()
    override suspend fun update(category: Category) = throw NotImplementedError()
    override suspend fun delete(category: Category) = throw NotImplementedError()
}

private class FakeEntryRepository(private val owedByInvoiceId: Map<Long, Double>) : IEntryRepository {
    override suspend fun invoiceOwed(invoiceId: Long): Double = owedByInvoiceId[invoiceId] ?: 0.0
    override suspend fun getEntriesByOperation(operationId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}
