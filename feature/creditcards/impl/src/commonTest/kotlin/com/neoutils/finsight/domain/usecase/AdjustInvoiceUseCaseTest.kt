package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes the D17 divergence of [AdjustInvoiceUseCase], the mirror image of
 * [AdjustBalanceUseCase]'s bug: on the update branch it used to call only
 * `ITransactionRepository.update`, which touches the legacy `transactions` row but
 * never the ledger — which `invoiceOwed` already reads — leaving the ledger leg
 * permanently stale. The assertion below fails against the pre-fix code and passes
 * after routing the write to both models.
 */
class AdjustInvoiceUseCaseTest {

    private val date = LocalDate(2026, 1, 10)
    private val card = CreditCard(id = 1, name = "Card", limit = 1000.0, closingDay = 5, dueDay = 15)
    private val invoice = Invoice(
        id = 1,
        creditCard = card,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `re-adjusting an invoice keeps the legacy leg and the ledger in sync`() = runTest {
        val stores = LedgerBackedStores()
        val useCase = AdjustInvoiceUseCase(
            repository = FakeTransactionRepository(stores),
            operationRepository = FakeOperationRepository(stores),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(stores)),
        )

        // First adjustment creates the adjustment operation; the second takes the update branch.
        useCase(invoice = invoice, target = 100.0, adjustmentDate = date).getOrNull()
        useCase(invoice = invoice, target = 200.0, adjustmentDate = date).getOrNull()

        assertEquals(
            stores.ledgerAmountByOperation.values.single(),
            stores.legacyTransactions.single().amount,
            "ledger leg diverged from the legacy row after re-adjustment",
        )
    }
}

/** See the twin in feature/accounts/impl for the rationale of the two-store model. */
class LedgerBackedStores {
    val legacyTransactions = mutableListOf<Transaction>()
    val ledgerAmountByOperation = mutableMapOf<Long, Double>()
    private var nextOperationId = 0L
    private var nextTransactionId = 0L

    fun create(transactions: List<Transaction>): Long {
        val operationId = ++nextOperationId
        transactions.forEach { transaction ->
            legacyTransactions += transaction.copy(id = ++nextTransactionId, operationId = operationId)
        }
        ledgerAmountByOperation[operationId] = transactions.sumOf { it.amount }
        return operationId
    }
}

class FakeOperationRepository(private val stores: LedgerBackedStores) : IOperationRepository {
    override suspend fun createOperation(
        title: String?,
        date: LocalDate,
        categoryId: Long?,
        recurringId: Long?,
        recurringCycle: Int?,
        installmentId: Long?,
        installmentNumber: Int?,
        transactions: List<Transaction>,
    ): Operation {
        val operationId = stores.create(transactions)
        return Operation(
            id = operationId,
            title = title,
            date = date,
            transactions = transactions.map { it.copy(operationId = operationId) },
        )
    }

    // Real behavior: rewrites only the ledger, never the legacy `transactions` row.
    override suspend fun updateOperation(id: Long, transaction: Transaction) {
        stores.ledgerAmountByOperation[id] = transaction.amount
    }

    override suspend fun deleteOperationById(id: Long) {
        stores.ledgerAmountByOperation.remove(id)
        stores.legacyTransactions.removeAll { it.operationId == id }
    }

    override fun observeAllOperations(): Flow<List<Operation>> = throw NotImplementedError()
    override fun observeOperationsBy(date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Operation>> = throw NotImplementedError()
    override fun observeOperationById(id: Long): Flow<Operation?> = throw NotImplementedError()
    override suspend fun getAllOperations(): List<Operation> = throw NotImplementedError()
    override suspend fun getOperationById(id: Long): Operation? = throw NotImplementedError()
    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
}

class FakeTransactionRepository(private val stores: LedgerBackedStores) : ITransactionRepository {
    // Real behavior: updates only the legacy `transactions` row.
    override suspend fun update(transaction: Transaction) {
        val index = stores.legacyTransactions.indexOfFirst { it.id == transaction.id }
        if (index >= 0) stores.legacyTransactions[index] = transaction
    }

    override suspend fun delete(transaction: Transaction) {
        stores.legacyTransactions.removeAll { it.id == transaction.id }
    }

    override suspend fun getTransactionsBy(
        type: Transaction.Type?,
        target: Transaction.Target?,
        date: LocalDate?,
        invoiceId: Long?,
        accountId: Long?,
    ): List<Transaction> = stores.legacyTransactions.filter { transaction ->
        (type == null || transaction.type == type) &&
            (target == null || transaction.target == target) &&
            (date == null || transaction.date == date) &&
            (invoiceId == null || transaction.invoice?.id == invoiceId) &&
            (accountId == null || transaction.account?.id == accountId)
    }

    override suspend fun insert(transaction: Transaction): Long = throw NotImplementedError()
    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getTransactionBy(id: Long): Transaction? = throw NotImplementedError()
    override fun observeTransactionsBy(type: Transaction.Type?, target: Transaction.Target?, date: LocalDate?, invoiceId: Long?, creditCardId: Long?, accountId: Long?): Flow<List<Transaction>> = throw NotImplementedError()
}

class FakeEntryRepository(private val stores: LedgerBackedStores) : IEntryRepository {
    override suspend fun getEntriesByOperation(operationId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>> = throw NotImplementedError()

    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double =
        stores.ledgerAmountByOperation.values.sum()

    override suspend fun invoiceOwed(invoiceId: Long): Double =
        stores.ledgerAmountByOperation.values.sum()

    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): com.neoutils.finsight.domain.repository.AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): com.neoutils.finsight.domain.repository.InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): com.neoutils.finsight.domain.repository.CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(categoryType: AccountType, startDate: LocalDate, endDate: LocalDate, siblingAccountIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
    override suspend fun categoryTotalsForInvoices(categoryType: AccountType, invoiceIds: List<Long>): Map<Long, Double> = throw NotImplementedError()
}
