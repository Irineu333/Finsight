package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.naturalBalanceOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

/**
 * Characterizes [AdjustBalanceUseCase] over the ledger: a re-adjustment must
 * recompute the adjustment from its own ledger leg, so the transaction ends up
 * describing the newly targeted balance rather than accumulating onto a stale
 * value (the D17 divergence, which the legacy double-write made possible).
 */
class AdjustBalanceUseCaseTest {

    private val date = LocalDate(2026, 1, 10)
    private val account = Account(id = 1, name = "Checking", type = AccountType.ASSET, currency = "BRL")

    @Test
    fun `re-adjusting a balance rewrites the adjustment from the ledger`() = runTest {
        val ledger = LedgerStore(account)
        val useCase = AdjustBalanceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )

        // First adjustment: balance 0 -> 100, creates the adjustment transaction.
        useCase(targetBalance = 100.0, adjustmentDate = date, account = account).getOrNull()
        // Second adjustment on the same date: 100 -> 150, takes the update branch.
        useCase(targetBalance = 150.0, adjustmentDate = date, account = account).getOrNull()

        assertEquals(150.0, ledger.accountBalance())
    }

    /**
     * The proof that `AdjustBalanceUseCase` did not have to change at all.
     *
     * Its idempotency defines "the existing adjustment" as *any* transaction on that
     * date, on that account, carrying an `EQUITY` leg. Had the conversion account
     * been an `EQUITY` row, a cross-currency transfer made on the same day would have
     * matched that predicate and been **rewritten** as the adjustment — losing the
     * transfer and inventing an adjustment nobody made. A type of its own is what
     * makes the predicate stop matching, with no line of this use case touched.
     */
    @Test
    fun `a same-day cross-currency transfer is not rewritten as the adjustment`() = runTest {
        val ledger = LedgerStore(account)
        val useCase = AdjustBalanceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )

        val transferId = ledger.seedCrossCurrencyTransfer(date)
        val transferEntries = ledger.entriesByTransaction.getValue(transferId)

        useCase(targetBalance = 100.0, adjustmentDate = date, account = account).getOrNull()

        // The transfer is untouched, and the adjustment is a transaction of its own.
        assertEquals(transferEntries, ledger.entriesByTransaction.getValue(transferId))
        assertEquals(2, ledger.entriesByTransaction.size)
    }

    /**
     * And the editability gate refuses the crossing without a gate of its own: it
     * counts **monetary** legs, and a conversion leg is not one (design D19).
     */
    @Test
    fun `a cross-currency transfer has two monetary legs and so falls in the existing gate`() = runTest {
        val ledger = LedgerStore(account)
        val transferId = ledger.seedCrossCurrencyTransfer(date)
        val transaction = Transaction(
            id = transferId,
            title = null,
            date = date,
            entries = ledger.entriesByTransaction.getValue(transferId),
        )

        assertEquals(2, transaction.monetaryEntries.size)
        assertEquals(4, transaction.entries.size)
    }
}

/**
 * The double-entry ledger as the writer would build it: an adjustment leg on the
 * account plus its EQUITY reconciliation counter-leg, keyed by transaction id.
 */
class LedgerStore(private val account: Account) {
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY, currency = "BRL")
    private val foreignAccount = Account(id = 2, name = "Chase", type = AccountType.ASSET, currency = "USD")
    private val conversionLocal =
        Account(id = 900, name = "Conversão", type = AccountType.CONVERSION, currency = "BRL")
    private val conversionForeign =
        Account(id = 901, name = "Conversão", type = AccountType.CONVERSION, currency = "USD")
    val entriesByTransaction = mutableMapOf<Long, List<Entry>>()
    val dateByTransaction = mutableMapOf<Long, LocalDate>()
    private var nextTransactionId = 0L

    fun write(transactionId: Long, legs: List<TransactionLeg>) {
        entriesByTransaction[transactionId] = legs.flatMap { leg ->
            val cents = (leg.amount * 100).roundToLong()
            listOf(
                Entry(transactionId = transactionId, account = account, amount = cents),
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

    /** BRL 550 out of [account] into a USD account, as the write boundary completes it. */
    fun seedCrossCurrencyTransfer(date: LocalDate): Long {
        val transactionId = ++nextTransactionId
        dateByTransaction[transactionId] = date
        entriesByTransaction[transactionId] = listOf(
            Entry(transactionId = transactionId, account = account, amount = -55_000),
            Entry(transactionId = transactionId, account = conversionLocal, amount = 55_000),
            Entry(transactionId = transactionId, account = conversionForeign, amount = -10_000),
            Entry(transactionId = transactionId, account = foreignAccount, amount = 10_000),
        )
        return transactionId
    }

    fun accountBalance(): Double =
        entriesByTransaction.values.flatten().naturalBalanceOf(account.id) / 100.0
}

class FakeTransactionRepository(private val ledger: LedgerStore) : ITransactionRepository {
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

    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) {
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
            .filter { (_, entries) -> accountId == null || entries.any { it.account.id == accountId } }
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

class FakeEntryRepository(private val ledger: LedgerStore) : IEntryRepository {
    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = ledger.accountBalance()

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun balanceUpToByCurrency(target: YearMonth, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType, excludedAccountIds: Set<Long>): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionOwedByCurrency(dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionFlowsByCurrency(dimensionId: Long): DimensionFlowsByCurrency = throw NotImplementedError()
    override suspend fun owedByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun flowsByDimensionByCurrency(dimensionIds: Collection<Long>): Map<Long, DimensionFlowsByCurrency> = throw NotImplementedError()
    override suspend fun liabilityMonthFlowsByCurrency(month: YearMonth): LiabilityMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun assetMonthFlowsByCurrency(month: YearMonth, yieldDimensionId: Long?): AssetMonthFlowsByCurrency = throw NotImplementedError()
    override suspend fun totalsByDimensionByCurrency(
        nominalType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun totalsByDimensionInScopeByCurrency(
        nominalType: AccountType,
        scopeDimensionIds: List<Long>,
    ): Map<Long?, MoneyByCurrency> = throw NotImplementedError()
    override suspend fun scopeStatsByCurrency(
        scopeAccountIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): ScopeStatsByCurrency = throw NotImplementedError()
}
