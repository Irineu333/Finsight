package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.InvoiceFlows
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [AdjustInvoiceUseCase] over the ledger, the mirror image of
 * [AdjustBalanceUseCase]: a re-adjustment must recompute the adjustment from its
 * own ledger leg, so the invoice ends up owing the newly targeted amount rather
 * than accumulating onto a stale value (the D17 divergence).
 */
class AdjustInvoiceUseCaseTest {

    private val date = LocalDate(2026, 1, 10)
    private val card = CreditCard(
        id = 1,
        name = "Card",
        limit = 1000.0,
        closingDay = 5,
        dueDay = 15,
        accountId = 10,
    )
    private val invoice = Invoice(
        id = 1,
        creditCard = card,
        dimensionId = 1,
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `re-adjusting an invoice rewrites the adjustment from the ledger`() = runTest {
        val ledger = InvoiceLedgerStore(card)
        val useCase = AdjustInvoiceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        )

        // First adjustment: owed 0 -> 100, creates the adjustment transaction.
        useCase(invoice = invoice, target = 100.0, adjustmentDate = date).getOrNull()
        // Second adjustment on the same date: 100 -> 200, takes the update branch.
        useCase(invoice = invoice, target = 200.0, adjustmentDate = date).getOrNull()

        assertEquals(200.0, ledger.dimensionOwed(invoice.id))
    }
}

/**
 * The double-entry ledger as [LedgerEntryWriter] would build it for an invoice
 * adjustment: the card's LIABILITY leg — the only one carrying the invoice id —
 * plus its EQUITY reconciliation counter-leg, keyed by transaction id.
 */
class InvoiceLedgerStore(card: CreditCard) {
    private val cardAccount = Account(id = card.accountId, name = card.name, type = AccountType.LIABILITY)
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY)
    val entriesByTransaction = mutableMapOf<Long, List<Entry>>()
    val dateByTransaction = mutableMapOf<Long, LocalDate>()
    private var nextTransactionId = 0L

    fun write(transactionId: Long, legs: List<TransactionLeg>) {
        entriesByTransaction[transactionId] = legs.flatMap { leg ->
            val cents = (leg.amount * 100).roundToLong()
            listOf(
                Entry(
                    transactionId = transactionId,
                    account = cardAccount,
                    amount = cents,
                    dimensionId = leg.invoice?.dimensionId,
                ),
                Entry(transactionId = transactionId, account = equity, amount = -cents),
            )
        }
    }

    fun create(date: LocalDate, legs: List<TransactionLeg>): Long {
        val transactionId = ++nextTransactionId
        dateByTransaction[transactionId] = date
        write(transactionId, legs)
        return transactionId
    }

    fun dimensionOwed(dimensionId: Long): Double = -entriesByTransaction.values
        .flatten()
        .filter { it.dimensionId == dimensionId }
        .sumOf { it.amount } / 100.0
}

class FakeTransactionRepository(private val ledger: InvoiceLedgerStore) : ITransactionRepository {
    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        val transactionId = ledger.create(intent.date, intent.legs)
        return Transaction(
            id = transactionId,
            title = intent.title,
            date = intent.date,
            entries = ledger.entriesByTransaction.getValue(transactionId),
        )
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> =
        intents.map { createTransaction(it) }

    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg) {
        ledger.dateByTransaction[id] = date
        ledger.write(id, listOf(leg))
    }

    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) {
        ledger.entriesByTransaction.remove(id)
        ledger.dateByTransaction.remove(id)
    }

    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> {
        val transactions = ledger.entriesByTransaction
            .filter { (id, _) -> date == null || ledger.dateByTransaction[id] == date }
            .filter { (_, entries) -> dimensionId == null || entries.any { it.dimensionId == dimensionId } }
            .map { (id, entries) ->
                Transaction(id = id, title = null, date = ledger.dateByTransaction.getValue(id), entries = entries)
            }
        return flowOf(transactions)
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
}

class FakeEntryRepository(private val ledger: InvoiceLedgerStore) : IEntryRepository {
    override suspend fun dimensionOwed(dimensionId: Long): Double = ledger.dimensionOwed(dimensionId)

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonth(month: YearMonth, dimensionId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()
    override suspend fun dimensionFlows(dimensionId: Long): InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun totalsByDimension(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()

    override suspend fun totalsByDimensionInScope(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, Double> = throw NotImplementedError()
    override suspend fun reportStats(scopeAccountIds: List<Long>, startDate: LocalDate, endDate: LocalDate): com.neoutils.finsight.domain.repository.ReportStats = throw NotImplementedError()
}
