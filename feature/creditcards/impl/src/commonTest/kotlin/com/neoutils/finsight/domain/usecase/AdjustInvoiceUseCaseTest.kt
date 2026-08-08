package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import com.neoutils.finsight.domain.repository.AssetMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.DimensionFlowsByCurrency
import com.neoutils.finsight.domain.repository.LiabilityMonthFlowsByCurrency
import com.neoutils.finsight.domain.repository.ScopeStatsByCurrency

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

    /**
     * The proof that `AdjustInvoiceUseCase` did not have to change either.
     *
     * Its idempotency is even broader than the account one — "any transaction on that
     * date carrying this invoice, with an `EQUITY` leg" — so a cross-currency invoice
     * payment made the same day would have been rewritten as the adjustment had the
     * conversion account been an `EQUITY` row. It stops matching because a conversion
     * leg is not `EQUITY`, and the owed amount is untouched because the conversion leg
     * carries no dimension (design D15).
     */
    @Test
    fun `a same-day cross-currency invoice payment is not rewritten as the adjustment`() = runTest {
        val ledger = InvoiceLedgerStore(card)
        val useCase = AdjustInvoiceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        )

        val dimensionId = invoice.dimensionId!!
        val paymentId = ledger.seedCrossCurrencyPayment(date, dimensionId)
        val paymentEntries = ledger.entriesByTransaction.getValue(paymentId)
        val owedAfterPayment = ledger.dimensionOwed(dimensionId)

        useCase(invoice = invoice, target = 300.0, adjustmentDate = date).getOrNull()

        assertEquals(paymentEntries, ledger.entriesByTransaction.getValue(paymentId))
        assertEquals(2, ledger.entriesByTransaction.size)
        // The adjustment moved the owed amount; the payment's own legs did not change.
        assertEquals(-100.0, owedAfterPayment)
    }
}

/**
 * The double-entry ledger as [LedgerEntryWriter] would build it for an invoice
 * adjustment: the card's LIABILITY leg — the only one carrying the invoice id —
 * plus its EQUITY reconciliation counter-leg, keyed by transaction id.
 */
class InvoiceLedgerStore(card: CreditCard) {
    private val cardAccount = Account(id = card.accountId, name = card.name, type = AccountType.LIABILITY, currency = "BRL")
    private val equity = Account(id = 999, name = "Reconciliation", type = AccountType.EQUITY, currency = "BRL")
    private val payingAccount = Account(id = 2, name = "Nubank", type = AccountType.ASSET, currency = "BRL")
    private val conversionLocal =
        Account(id = 900, name = "Conversão", type = AccountType.CONVERSION, currency = "BRL")
    private val conversionForeign =
        Account(id = 901, name = "Conversão", type = AccountType.CONVERSION, currency = "USD")
    private val foreignCard = Account(id = 11, name = "Chase card", type = AccountType.LIABILITY, currency = "USD")
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
                    dimensionId = leg.dimensionId,
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

    /** BRL 550 out of an account, settling USD 100 of a foreign card's invoice. */
    fun seedCrossCurrencyPayment(date: LocalDate, dimensionId: Long): Long {
        val transactionId = ++nextTransactionId
        dateByTransaction[transactionId] = date
        entriesByTransaction[transactionId] = listOf(
            Entry(transactionId = transactionId, account = payingAccount, amount = -55_000),
            Entry(transactionId = transactionId, account = conversionLocal, amount = 55_000),
            Entry(transactionId = transactionId, account = conversionForeign, amount = -10_000),
            Entry(
                transactionId = transactionId,
                account = foreignCard,
                amount = 10_000,
                dimensionId = dimensionId,
            ),
        )
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
    override suspend fun dimensionOwedByCurrency(dimensionId: Long) =
        MoneyByCurrency.of("BRL", ledger.dimensionOwed(dimensionId))

    override suspend fun getEntriesByTransaction(transactionId: Long): List<Entry> = throw NotImplementedError()
    override fun observeEntriesByTransaction(transactionId: Long): Flow<List<Entry>> = throw NotImplementedError()
    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun balance(accountId: Long): Double = throw NotImplementedError()
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false
    override suspend fun accountFlows(month: YearMonth, accountId: Long, yieldDimensionId: Long?): AccountFlows = throw NotImplementedError()
    override suspend fun dimensionEntryCountInMonth(month: YearMonth, dimensionId: Long): Int = throw NotImplementedError()

    override suspend fun accountBalanceUpTo(accountId: Long, target: YearMonth): Double = throw NotImplementedError()
    override suspend fun balanceUpToByCurrency(target: YearMonth): MoneyByCurrency = throw NotImplementedError()
    override suspend fun naturalBalanceUpToByCurrency(target: YearMonth, type: AccountType): MoneyByCurrency = throw NotImplementedError()
    override suspend fun dimensionBalanceInMonthByCurrency(month: YearMonth, dimensionId: Long): MoneyByCurrency = throw NotImplementedError()
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
