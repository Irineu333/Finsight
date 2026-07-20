@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.AccountType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
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

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY)
    private val contraAccount = Account(id = 20, name = "Contra", type = AccountType.EXPENSE)

    /** The card's LIABILITY leg — the only one carrying the invoice — plus its contra leg. */
    private fun op(id: Long, type: TransactionType, amount: Double): Transaction {
        val cents = (amount * 100).toLong()
        val signed = if (type == TransactionType.EXPENSE) -cents else cents
        return Transaction(
            id = id,
            title = null,
            date = LocalDate(2026, 3, 10),
            targetCreditCard = card,
            targetInvoice = invoice,
            entries = listOf(
                Entry(transactionId = id, account = cardAccount, amount = signed, invoiceId = invoice.id),
                Entry(transactionId = id, account = contraAccount, amount = -signed),
            ),
        )
    }

    @Test
    fun `invoice summary characterizes the card leg sums and owed total`() = runTest(dispatcher) {
        val transactions = listOf(
            op(1, TransactionType.EXPENSE, 60.0),
            op(2, TransactionType.EXPENSE, 40.0),
            op(3, TransactionType.ADJUSTMENT, 10.0),
            op(4, TransactionType.INCOME, 30.0), // advance payment
        )
        val vm = InvoiceTransactionsViewModel(
            creditCardId = 1,
            creditCardRepository = FakeCreditCardRepository(card),
            invoiceRepository = FakeInvoiceRepository(listOf(invoice)),
            transactionRepository = FakeTransactionRepository(transactions),
            categoryRepository = FakeCategoryRepository(),
            entryRepository = FakeEntryRepository(
                owedByInvoiceId = mapOf(1L to 70.0),
                flowsByInvoiceId = mapOf(
                    1L to com.neoutils.finsight.domain.repository.InvoiceFlows(expense = 100.0, advancePayment = 30.0, adjustment = 10.0),
                ),
            ),
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

private class FakeTransactionRepository(private val transactions: List<Transaction>) : ITransactionRepository {
    override fun observeTransactionsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
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

private class FakeEntryRepository(
    private val owedByInvoiceId: Map<Long, Double>,
    private val flowsByInvoiceId: Map<Long, com.neoutils.finsight.domain.repository.InvoiceFlows> = emptyMap(),
) : IEntryRepository {
    override suspend fun invoiceOwed(invoiceId: Long): Double = owedByInvoiceId[invoiceId] ?: 0.0
    override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows =
        flowsByInvoiceId[invoiceId] ?: com.neoutils.finsight.domain.repository.InvoiceFlows(0.0, 0.0, 0.0)
    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}
