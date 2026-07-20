package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.CardMonthFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.InvoiceFlows
import com.neoutils.finsight.extension.naturalBalanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Characterizes [AdjustBalanceUseCase] over the ledger: a re-adjustment must
 * recompute the adjustment from its own ledger leg, so the operation ends up
 * describing the newly targeted balance rather than accumulating onto a stale
 * value (the D17 divergence, which the legacy double-write made possible).
 */
class AdjustBalanceUseCaseTest {

    private val date = LocalDate(2026, 1, 10)
    private val account = Account(id = 1, name = "Checking", type = AccountType.ASSET)

    @Test
    fun `re-adjusting a balance rewrites the adjustment from the ledger`() = runTest {
        val ledger = LedgerStore(account)
        val useCase = AdjustBalanceUseCase(
            operationRepository = FakeOperationRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )

        // First adjustment: balance 0 -> 100, creates the adjustment operation.
        useCase(targetBalance = 100.0, adjustmentDate = date, account = account).getOrNull()
        // Second adjustment on the same date: 100 -> 150, takes the update branch.
        useCase(targetBalance = 150.0, adjustmentDate = date, account = account).getOrNull()

        assertEquals(150.0, ledger.accountBalance())
    }
}

/**
 * The double-entry ledger as the writer would build it: an adjustment leg on the
 * account plus its EQUITY reconciliation counter-leg, keyed by operation id.
 */
class LedgerStore(private val account: Account) {
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY)
    val entriesByOperation = mutableMapOf<Long, List<Entry>>()
    val dateByOperation = mutableMapOf<Long, LocalDate>()
    private var nextOperationId = 0L

    fun write(transactionId: Long, legs: List<OperationLeg>) {
        entriesByOperation[transactionId] = legs.flatMap { leg ->
            val cents = (leg.amount * 100).roundToLong()
            listOf(
                Entry(transactionId = transactionId, account = leg.account ?: account, amount = cents),
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

    fun accountBalance(): Double =
        entriesByOperation.values.flatten().naturalBalanceOf(account.id) / 100.0
}

class FakeOperationRepository(private val ledger: LedgerStore) : IOperationRepository {
    override suspend fun createOperation(intent: OperationIntent): Operation {
        val transactionId = ledger.create(intent.date, intent.legs)
        return Operation(
            id = transactionId,
            title = intent.title,
            date = intent.date,
            entries = ledger.entriesByOperation.getValue(transactionId),
        )
    }

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
            .filter { (_, entries) -> accountId == null || entries.any { it.account.id == accountId } }
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

class FakeEntryRepository(private val ledger: LedgerStore) : IEntryRepository {
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long?): Double = ledger.accountBalance()

    override suspend fun getEntriesByOperation(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByOperation(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun invoiceOwed(invoiceId: Long): Double = throw NotImplementedError()
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
