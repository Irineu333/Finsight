package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
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
        openingMonth = YearMonth(2026, 1),
        closingMonth = YearMonth(2026, 2),
        dueMonth = YearMonth(2026, 3),
        status = Invoice.Status.OPEN,
    )

    @Test
    fun `re-adjusting an invoice rewrites the adjustment from the ledger`() = runTest {
        val ledger = InvoiceLedgerStore(card)
        val useCase = AdjustInvoiceUseCase(
            operationRepository = FakeOperationRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        )

        // First adjustment: owed 0 -> 100, creates the adjustment operation.
        useCase(invoice = invoice, target = 100.0, adjustmentDate = date).getOrNull()
        // Second adjustment on the same date: 100 -> 200, takes the update branch.
        useCase(invoice = invoice, target = 200.0, adjustmentDate = date).getOrNull()

        assertEquals(200.0, ledger.invoiceOwed(invoice.id))
    }
}

/**
 * The double-entry ledger as [LedgerEntryWriter] would build it for an invoice
 * adjustment: the card's LIABILITY leg — the only one carrying the invoice id —
 * plus its EQUITY reconciliation counter-leg, keyed by operation id.
 */
class InvoiceLedgerStore(card: CreditCard) {
    private val cardAccount = Account(id = card.accountId!!, name = card.name, type = AccountType.LIABILITY)
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY)
    val entriesByOperation = mutableMapOf<Long, List<Entry>>()
    val dateByOperation = mutableMapOf<Long, LocalDate>()
    private var nextOperationId = 0L

    fun write(transactionId: Long, legs: List<OperationLeg>) {
        entriesByOperation[transactionId] = legs.flatMap { leg ->
            val cents = (leg.amount * 100).roundToLong()
            listOf(
                Entry(
                    transactionId = transactionId,
                    account = cardAccount,
                    amount = cents,
                    invoiceId = leg.invoice?.id,
                ),
                Entry(transactionId = transactionId, account = equity, amount = -cents),
            )
        }
    }

    fun create(date: LocalDate, legs: List<OperationLeg>): Long {
        val transactionId = ++nextOperationId
        dateByOperation[transactionId] = date
        write(transactionId, legs)
        return transactionId
    }

    fun invoiceOwed(invoiceId: Long): Double = -entriesByOperation.values
        .flatten()
        .filter { it.invoiceId == invoiceId }
        .sumOf { it.amount } / 100.0
}

class FakeOperationRepository(private val ledger: InvoiceLedgerStore) : IOperationRepository {
    override suspend fun createOperation(intent: OperationIntent): Operation {
        val transactionId = ledger.create(intent.date, intent.legs)
        return Operation(
            id = transactionId,
            title = intent.title,
            date = intent.date,
            entries = ledger.entriesByOperation.getValue(transactionId),
        )
    }

    override suspend fun createOperations(intents: List<OperationIntent>): List<Operation> =
        intents.map { createOperation(it) }

    override suspend fun updateOperation(id: Long, title: String?, date: LocalDate, leg: OperationLeg) {
        ledger.dateByOperation[id] = date
        ledger.write(id, listOf(leg))
    }

    override suspend fun deleteOperationById(id: Long) {
        ledger.entriesByOperation.remove(id)
        ledger.dateByOperation.remove(id)
    }

    override fun observeOperationsBy(
        date: LocalDate?,
        invoiceId: Long?,
        creditCardId: Long?,
        accountId: Long?,
    ): Flow<List<Operation>> {
        val operations = ledger.entriesByOperation
            .filter { (id, _) -> date == null || ledger.dateByOperation[id] == date }
            .filter { (_, entries) -> invoiceId == null || entries.any { it.invoiceId == invoiceId } }
            .map { (id, entries) ->
                Operation(id = id, title = null, date = ledger.dateByOperation.getValue(id), entries = entries)
            }
        return flowOf(operations)
    }

    override fun observeAllOperations(): Flow<List<Operation>> = throw NotImplementedError()
    override fun observeOperationById(id: Long): Flow<Operation?> = throw NotImplementedError()
    override suspend fun getAllOperations(): List<Operation> = throw NotImplementedError()
    override suspend fun getOperationById(id: Long): Operation? = throw NotImplementedError()
    override suspend fun deleteTransactionOperationsByCreditCard(creditCardId: Long) = throw NotImplementedError()
}

class FakeEntryRepository(private val ledger: InvoiceLedgerStore) : IEntryRepository {
    override suspend fun invoiceOwed(invoiceId: Long): Double = ledger.invoiceOwed(invoiceId)

    override suspend fun getEntriesByOperation(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = throw NotImplementedError()
    override suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double = throw NotImplementedError()
    override suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows = throw NotImplementedError()
    override suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int = throw NotImplementedError()
    override suspend fun invoiceFlows(invoiceId: Long): InvoiceFlows = throw NotImplementedError()
    override suspend fun cardMonthFlows(month: YearMonth): CardMonthFlows = throw NotImplementedError()
    override suspend fun netWorth(): Double = throw NotImplementedError()
    override suspend fun categoryTotals(
        categoryType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long, Double> = throw NotImplementedError()

    override suspend fun categoryTotalsForInvoices(
        categoryType: AccountType,
        invoiceIds: List<Long>,
    ): Map<Long, Double> = throw NotImplementedError()
}
