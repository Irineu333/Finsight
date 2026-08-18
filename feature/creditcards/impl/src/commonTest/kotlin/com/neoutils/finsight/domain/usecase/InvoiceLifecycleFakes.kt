@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlinx.datetime.plus
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * The doubles the invoice lifecycle tests share — closing, paying, and the limit that
 * follows from both.
 *
 * They record rather than answer: what these use cases decide is *which* row they
 * write and *whether* they write it at all, so a fake that only returns values would
 * leave the interesting half unobserved.
 */

internal fun testCard(
    limit: Double = 1_000.0,
    closingDay: Int = 5,
    dueDay: Int = 15,
) = CreditCard(
    id = 1,
    name = "Card",
    limit = limit,
    closingDay = closingDay,
    dueDay = dueDay,
    accountId = 10,
)

internal fun testInvoice(
    id: Long = 1,
    openingMonth: YearMonth = YearMonth(2026, 1),
    status: Invoice.Status = Invoice.Status.OPEN,
    card: CreditCard = testCard(),
    dimensionId: Long? = id * 100,
) = Invoice(
    id = id,
    creditCard = card,
    dimensionId = dimensionId,
    openingMonth = openingMonth,
    closingMonth = openingMonth.plus(1, DateTimeUnit.MONTH),
    dueMonth = openingMonth.plus(1, DateTimeUnit.MONTH),
    status = status,
)

/** Holds invoices and remembers every write, so a test can assert what was *not* written. */
internal class RecordingInvoiceStore(vararg seed: Invoice) : IInvoiceRepository {

    private val rows = seed.associateBy { it.id }.toMutableMap()

    val updates = mutableListOf<Invoice>()
    val inserts = mutableListOf<Invoice>()

    fun byId(id: Long) = rows[id]

    override suspend fun getInvoiceById(id: Long): Invoice? = rows[id]
    override suspend fun getInvoicesByCreditCard(creditCardId: Long) =
        rows.values.filter { it.creditCard.id == creditCardId }.sortedBy { it.openingMonth }

    override suspend fun getUnpaidInvoicesByCreditCard(creditCardId: Long) =
        rows.values.filter { it.creditCard.id == creditCardId && !it.status.isPaid }

    override suspend fun getUnpaidInvoicesByCreditCards(
        creditCardIds: Collection<Long>,
    ): Map<Long, List<Invoice>> = creditCardIds
        .associateWith { getUnpaidInvoicesByCreditCard(it) }
        .filterValues { it.isNotEmpty() }

    override suspend fun update(invoice: Invoice) {
        rows[invoice.id] = invoice
        updates += invoice
    }

    override suspend fun insert(invoice: Invoice): Invoice {
        val stored = invoice.copy(id = (rows.keys.maxOrNull() ?: 0) + 1)
        rows[stored.id] = stored
        inserts += stored
        return stored
    }

    override suspend fun getOpenInvoice(creditCardId: Long): Invoice? =
        rows.values.firstOrNull { it.creditCard.id == creditCardId && it.status.isOpen }

    override suspend fun getAllInvoices(): List<Invoice> = rows.values.toList()
    override suspend fun deleteById(id: Long) { rows.remove(id) }

    override fun observeAllInvoices(): Flow<List<Invoice>> = notUnderTest()
    override fun observeInvoicesByCreditCard(creditCardId: Long): Flow<List<Invoice>> = notUnderTest()
    override fun observeInvoiceById(invoiceId: Long): Flow<Invoice?> = notUnderTest()
    override fun observeOpenInvoice(creditCardId: Long): Flow<Invoice?> = notUnderTest()
    override fun observeAvailableInvoices(creditCardId: Long): Flow<List<Invoice>> = notUnderTest()
    override fun observeUnpaidInvoice(creditCardId: Long): Flow<Invoice?> = notUnderTest()
    override fun observeUnpaidInvoices(): Flow<List<Invoice>> = notUnderTest()
}

internal class SingleCardRepository(private val card: CreditCard) : ICreditCardRepository {
    override suspend fun getCreditCardById(creditCardId: Long): CreditCard? = card
    override fun observeCreditCardById(creditCardId: Long): Flow<CreditCard?> = notUnderTest()
    override fun observeAllCreditCards(): Flow<List<CreditCard>> = notUnderTest()
    override fun observeAllCreditCardsIncludingClosed(): Flow<List<CreditCard>> = notUnderTest()
    override suspend fun getAllCreditCards(): List<CreditCard> = listOf(card)
    override suspend fun getAllCreditCardsIncludingClosed(): List<CreditCard> = listOf(card)
    override suspend fun insert(creditCard: CreditCard, currency: String): Long = notUnderTest()
    override suspend fun currencyForNewCard(): String = notUnderTest()
    override suspend fun update(creditCard: CreditCard) = notUnderTest()
    override suspend fun delete(creditCard: CreditCard) = notUnderTest()
    override suspend fun unarchive(accountId: Long) = notUnderTest()
}

/** Captures the intent the payment builds, or refuses to write at all. */
internal class RecordingTransactionWriter(
    private val failure: Throwable? = null,
) : ITransactionRepository {

    var captured: TransactionIntent? = null
        private set

    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        failure?.let { throw it }
        captured = intent
        // Entries are left empty: what the caller does with the written transaction is
        // nothing, and hydrating legs into accounts would be inventing a ledger.
        return Transaction(id = 1, title = intent.title, date = intent.date)
    }

    override fun observeAllTransactions(): Flow<List<Transaction>> = notUnderTest()
    override fun observeTransactionsBy(date: LocalDate?, dimensionId: Long?, accountId: Long?): Flow<List<Transaction>> = notUnderTest()
    override fun observeTransactionById(id: Long): Flow<Transaction?> = notUnderTest()
    override suspend fun getAllTransactions(): List<Transaction> = notUnderTest()
    override suspend fun getTransactionById(id: Long): Transaction? = notUnderTest()
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> = notUnderTest()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = notUnderTest()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) = notUnderTest()
    override suspend fun deleteTransactionById(id: Long) = notUnderTest()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = notUnderTest()
}

/** A clock stopped on a date, so "in the future" means something a test can state. */
internal class StoppedClock(private val date: LocalDate) : Clock {
    override fun now(): Instant = Instant.parse("${date}T12:00:00Z")
}

private fun notUnderTest(): Nothing = error("not part of the invoice lifecycle under test")
