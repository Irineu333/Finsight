package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.repository.AccountFlows
import com.neoutils.finsight.domain.repository.DimensionFlows
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.repository.LiabilityMonthFlows
import com.neoutils.finsight.test.StubEntryRepository
import com.neoutils.finsight.test.brl
import kotlin.math.roundToLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

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
     * The invoice-side mirror of the same rescue: this idempotence is even easier to
     * break, because it does not even ask which account the `EQUITY` leg is on — any
     * `EQUITY` leg on a transaction carrying this invoice is "the adjustment". A
     * cross-currency invoice payment made the same day would have matched it, and the
     * payment would have been rewritten into an adjustment. It does not, because a
     * conversion leg is not an `EQUITY` leg.
     */
    @Test
    fun `a cross-currency invoice payment of the same day is not mistaken for the adjustment`() = runTest {
        val ledger = InvoiceLedgerStore(card)
        val cardAccount = Account(id = card.accountId, name = card.name, type = AccountType.LIABILITY, currency = "USD")
        val payer = Account(id = 20, name = "Checking", type = AccountType.ASSET)
        val conversionBrl = Account(id = 900, name = "Conversão", type = AccountType.CONVERSION)
        val conversionUsd =
            Account(id = 901, name = "Conversão", type = AccountType.CONVERSION, currency = "USD")

        ledger.dateByTransaction[PAYMENT_ID] = date
        ledger.entriesByTransaction[PAYMENT_ID] = listOf(
            Entry(transactionId = PAYMENT_ID, account = payer, amount = -55_000),
            Entry(transactionId = PAYMENT_ID, account = conversionBrl, amount = 55_000),
            Entry(transactionId = PAYMENT_ID, account = conversionUsd, amount = -10_000, currency = "USD"),
            Entry(
                transactionId = PAYMENT_ID,
                account = cardAccount,
                amount = 10_000,
                currency = "USD",
                dimensionId = invoice.dimensionId,
            ),
        )

        AdjustInvoiceUseCase(
            transactionRepository = FakeTransactionRepository(ledger),
            calculateInvoiceUseCase = CalculateInvoiceUseCase(FakeEntryRepository(ledger)),
        )(invoice = invoice, target = 500.0, adjustmentDate = date).getOrNull()

        assertEquals(4, ledger.entriesByTransaction.getValue(PAYMENT_ID).size)
        assertEquals(
            1,
            ledger.entriesByTransaction
                .filterKeys { it != PAYMENT_ID }
                .count { (_, entries) -> entries.any { it.account.type == AccountType.EQUITY } },
        )
    }

    private companion object {
        const val PAYMENT_ID = 500L
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

internal class FakeEntryRepository(private val ledger: InvoiceLedgerStore) : StubEntryRepository() {
    override suspend fun dimensionOwed(dimensionId: Long) = brl(ledger.dimensionOwed(dimensionId))

    override fun observeLedgerChanges(): Flow<Unit> = flowOf(Unit)
    override suspend fun hasEntries(accountId: Long): Boolean = false
    override suspend fun hasEntriesForDimension(dimensionId: Long): Boolean = false

}
