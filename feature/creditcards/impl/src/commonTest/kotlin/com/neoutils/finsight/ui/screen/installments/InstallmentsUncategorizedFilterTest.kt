@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.installments

import app.cash.turbine.test
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.ui.mapper.InstallmentUiMapper
import com.neoutils.finsight.ui.screen.invoiceTransactions.FakeCategoryRepository
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
 * The unclassified cut on the instalments screen — the fifth surface offering the filter,
 * and the one whose menu is built from the categories present rather than from all of them.
 */
class InstallmentsUncategorizedFilterTest {

    private val dispatcher = StandardTestDispatcher()

    @BeforeTest fun setup() = Dispatchers.setMain(dispatcher)
    @AfterTest fun tearDown() = Dispatchers.resetMain()

    private val cardAccount = Account(id = 10, name = "Card", type = AccountType.LIABILITY, currency = "BRL")
    private val expenseAccount = Account(id = 20, name = "Expense", type = AccountType.EXPENSE, currency = "BRL")

    private val installment = Installment(id = 1, count = 2, totalAmount = 200.0)

    private fun charge(id: Long, number: Int, nominalDimensionId: Long?) = Transaction(
        id = id,
        title = "Charge $number",
        date = LocalDate(2026, 3, number),
        installmentId = installment.id,
        installmentNumber = number,
        entries = listOf(
            Entry(transactionId = id, account = cardAccount, amount = -10_000, dimensionId = 1),
            Entry(transactionId = id, account = expenseAccount, amount = 10_000, dimensionId = nominalDimensionId),
        ),
    )

    private val classified = charge(id = 1, number = 1, nominalDimensionId = 77)
    private val loose = charge(id = 2, number = 2, nominalDimensionId = null)

    private fun viewModel(charges: List<Transaction> = listOf(classified, loose)) = InstallmentsViewModel(
        installmentRepository = SingleInstallment(installment),
        transactionRepository = ChargeStore(charges),
        categoryRepository = FakeCategoryRepository(),
        invoiceRepository = NoInvoices,
        installmentUiMapper = InstallmentUiMapper(),
    )

    private suspend fun app.cash.turbine.TurbineTestContext<InstallmentsUiState>.awaitContent(
        predicate: (InstallmentsUiState.Content) -> Boolean = { true },
    ): InstallmentsUiState.Content {
        var state = awaitItem()
        while (state !is InstallmentsUiState.Content || !predicate(state)) state = awaitItem()
        return state
    }

    @Test
    fun `the cut keeps the charge whose nominal leg carries no dimension`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            val all = awaitContent()
            assertEquals(
                listOf(classified.id, loose.id),
                all.transactions.map { it.transaction.id }.sorted(),
                "both charges are listed before any cut",
            )

            vm.onAction(InstallmentsAction.SelectSubject(SpendingSubject.Uncategorized))

            val cut = awaitContent { it.selectedSubject == SpendingSubject.Uncategorized }
            assertEquals(listOf(loose.id), cut.transactions.map { it.transaction.id })
        }
    }

    @Test
    fun `the value is offered only when a charge of this installment is unclassified`() =
        runTest(dispatcher) {
            viewModel().uiState.test {
                assertEquals(true, awaitContent().mustShowUncategorizedFilter)
            }

            viewModel(charges = listOf(classified)).uiState.test {
                assertEquals(false, awaitContent().mustShowUncategorizedFilter)
            }
        }

    @Test
    fun `selecting another installment returns the axis to neutral`() = runTest(dispatcher) {
        val vm = viewModel()

        vm.uiState.test {
            awaitContent()
            vm.onAction(InstallmentsAction.SelectSubject(SpendingSubject.Uncategorized))
            awaitContent { it.selectedSubject == SpendingSubject.Uncategorized }

            vm.onAction(InstallmentsAction.SelectInstallment(0))

            val reset = awaitContent { it.selectedSubject == null }
            assertEquals(
                listOf(classified.id, loose.id),
                reset.transactions.map { it.transaction.id }.sorted(),
            )
        }
    }
}

private class SingleInstallment(private val installment: Installment) : IInstallmentRepository {
    override fun observeAllInstallments(): Flow<List<Installment>> =
        MutableStateFlow(listOf(installment))

    override suspend fun getAllInstallments(): List<Installment> = listOf(installment)
    override suspend fun getInstallmentById(id: Long): Installment? =
        installment.takeIf { it.id == id }

    override suspend fun createInstallment(count: Int, totalAmount: Double): Long =
        throw NotImplementedError()

    override suspend fun updateInstallment(id: Long, count: Int, totalAmount: Double) =
        throw NotImplementedError()

    override suspend fun deleteInstallmentById(id: Long) = throw NotImplementedError()
}

/** No invoice resolves, so no charge reads as settled — not what these tests are about. */
private object NoInvoices : IInvoiceRepository {
    override fun observeAllInvoices(): Flow<List<Invoice>> = MutableStateFlow(emptyList())
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = observeAllInvoices()
    override fun observeInvoiceById(dimensionId: Long): Flow<Invoice?> = MutableStateFlow(null)
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = MutableStateFlow(null)
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = observeAllInvoices()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = MutableStateFlow(null)
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = observeAllInvoices()
    override fun observeInvoicesToSettle(month: YearMonth): Flow<List<Invoice>> = throw NotImplementedError()
    override suspend fun getAllInvoices(): List<Invoice> = emptyList()
    override suspend fun getInvoicesByCreditCard(creditCardId: Long): List<Invoice> = emptyList()
    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long): List<Invoice> = emptyList()
    override suspend fun getInvoiceById(id: Long): Invoice? = null
    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? = null
    override suspend fun insert(invoice: Invoice): Invoice = throw NotImplementedError()
    override suspend fun update(invoice: Invoice) = throw NotImplementedError()
    override suspend fun deleteById(id: Long) = throw NotImplementedError()
}

private class ChargeStore(private val transactions: List<Transaction>) : ITransactionRepository {
    override fun observeAllTransactions(): Flow<List<Transaction>> = MutableStateFlow(transactions)
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> =
        observeAllTransactions()

    override fun observeTransactionById(id: Long): Flow<Transaction?> =
        MutableStateFlow(transactions.firstOrNull { it.id == id })

    override suspend fun getAllTransactions(): List<Transaction> = transactions
    override suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction> =
        transactions.filter { it.id in ids }

    override suspend fun getTransactionById(id: Long): Transaction? = transactions.firstOrNull { it.id == id }
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    ) = throw NotImplementedError()

    override suspend fun deleteTransactionsByIds(ids: List<Long>) = throw NotImplementedError()
    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
}
