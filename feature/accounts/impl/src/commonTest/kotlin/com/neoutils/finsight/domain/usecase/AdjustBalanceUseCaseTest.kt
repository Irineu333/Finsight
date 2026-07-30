package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountBalance
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.extension.naturalBalanceOf
import com.neoutils.finsight.test.StubEntryRepository
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Characterizes [AdjustBalanceUseCase] over the ledger: a re-adjustment must
 * recompute the adjustment from its own ledger leg, so the transaction ends up
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
     * The behaviour a conversion account of its own type exists to save.
     *
     * The idempotence above finds "the existing adjustment" as *the transaction on this
     * date with a leg on this account and an `EQUITY` counter-leg*. Had the exchange
     * residue been posted to `EQUITY`, a cross-currency transfer made the same day on the
     * same account would satisfy that predicate — and the use case would rewrite the
     * transfer instead of writing an adjustment. Nothing in production changes to make
     * this pass; if it fails, the residue is landing on the wrong type.
     */
    @Test
    fun `a cross-currency transfer of the same day is not mistaken for the adjustment`() = runTest {
        val ledger = LedgerStore(account)
        val foreign = Account(id = 2, name = "Chase", type = AccountType.ASSET, currency = "USD")
        val conversionBrl = Account(id = 900, name = "Conversão", type = AccountType.CONVERSION)
        val conversionUsd =
            Account(id = 901, name = "Conversão", type = AccountType.CONVERSION, currency = "USD")

        ledger.dateByTransaction[TRANSFER_ID] = date
        ledger.entriesByTransaction[TRANSFER_ID] = listOf(
            Entry(transactionId = TRANSFER_ID, account = account, amount = -55_000),
            Entry(transactionId = TRANSFER_ID, account = conversionBrl, amount = 55_000),
            Entry(transactionId = TRANSFER_ID, account = conversionUsd, amount = -10_000, currency = "USD"),
            Entry(transactionId = TRANSFER_ID, account = foreign, amount = 10_000, currency = "USD"),
        )

        AdjustBalanceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateBalanceUseCase = CalculateBalanceUseCase(FakeEntryRepository(ledger)),
        )(targetBalance = 0.0, adjustmentDate = date, account = account).getOrNull()

        // The transfer is untouched, and the adjustment is a transaction of its own.
        assertEquals(4, ledger.entriesByTransaction.getValue(TRANSFER_ID).size)
        assertEquals(
            1,
            ledger.entriesByTransaction
                .filterKeys { it != TRANSFER_ID }
                .count { (_, entries) -> entries.any { it.account.type == AccountType.EQUITY } },
        )
    }

    private companion object {
        const val TRANSFER_ID = 500L
    }
}

/**
 * The double-entry ledger as the writer would build it: an adjustment leg on the
 * account plus its EQUITY reconciliation counter-leg, keyed by transaction id.
 */
class LedgerStore(private val account: Account) {
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY)
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

internal class FakeEntryRepository(private val ledger: LedgerStore) : StubEntryRepository() {
    override suspend fun balanceUpTo(target: YearMonth, accountId: Long) =
        AccountBalance("BRL", ledger.accountBalance())

    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
}
