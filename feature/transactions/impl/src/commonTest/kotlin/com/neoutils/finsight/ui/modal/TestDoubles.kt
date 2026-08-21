package com.neoutils.finsight.ui.modal

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.datetime.LocalDate

class FakeCrashlytics : Crashlytics {
    val recorded = mutableListOf<Throwable>()
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) {
        recorded += e
    }
}

class FakeTransactionRepository(
    stored: List<Transaction> = emptyList(),
) : ITransactionRepository {

    private val byId = MutableSharedFlow<Transaction?>(replay = 1)

    /** What the store already holds, for the reads a use case does before it writes. */
    private val stored = stored.associateByTo(mutableMapOf()) { it.id }

    /** What was written straight through this repository, rather than by a cycle. */
    val created = mutableListOf<TransactionIntent>()

    /** Every rewrite this repository was asked for, in the order they arrived. */
    val rewritten = mutableListOf<Rewrite>()

    /** The identities this repository was asked to remove. */
    val deleted = mutableListOf<Long>()

    /** One call of [updateTransaction], kept whole so a test can read what the rewrite carried. */
    data class Rewrite(
        val id: Long,
        val title: String?,
        val date: LocalDate,
        val leg: TransactionLeg,
        val contra: ContraLeg?,
    )

    fun emit(transaction: Transaction?) {
        byId.tryEmit(transaction)
    }

    override fun observeTransactionById(id: Long): Flow<Transaction?> = byId

    override fun observeAllTransactions(): Flow<List<Transaction>> = throw NotImplementedError()
    override fun observeTransactionsBy(
        date: LocalDate?,
        dimensionId: Long?,
        accountId: Long?,
    ): Flow<List<Transaction>> = throw NotImplementedError()
    override suspend fun getAllTransactions(): List<Transaction> = throw NotImplementedError()

    override suspend fun getTransactionsBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> = throw NotImplementedError()
    override suspend fun getTransactionById(id: Long): Transaction? = stored[id]
    override suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long> =
        ids.filterTo(mutableSetOf()) { it in stored }
    override suspend fun createTransaction(intent: TransactionIntent): Transaction {
        created += intent
        return transaction(id = created.size.toLong())
    }

    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) {
        rewritten += Rewrite(id, title, date, leg, contra)
        // The rewrite is total in the real repository, so what a later read sees here is the
        // edited row and not the one the test seeded.
        stored[id]?.let { stored[id] = it.copy(title = title, date = date) }
    }
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) {
        deleted += id
    }
}

/**
 * Records what a recurring would be born as, without a database.
 *
 * [created] holds the three pieces the unit of work receives — the template, the intent
 * of its first cycle and the occurrence that records it — so a test can assert on the
 * anchoring and the cycle without reaching for Room.
 */
class RecordingRecurringRepository(
    private val failure: Throwable? = null,
) : IRecurringRepository {

    data class Created(
        val recurring: Recurring,
        val firstCycle: TransactionIntent,
        val occurrence: RecurringOccurrence,
    )

    val created = mutableListOf<Created>()

    override suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction {
        failure?.let { throw it }
        created += Created(recurring, firstCycle, occurrence)
        return transaction(id = 99L)
    }

    override fun observeAllRecurring(): Flow<List<Recurring>> = throw NotImplementedError()
    override fun observeRecurringById(id: Long): Flow<Recurring?> = throw NotImplementedError()
    override suspend fun getRecurringById(id: Long): Recurring? = throw NotImplementedError()
    override suspend fun hasRecurringForAccount(accountId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasRecurringForCategory(categoryId: Long): Boolean = throw NotImplementedError()
    override suspend fun hasTransactionForRecurring(recurringId: Long): Boolean = throw NotImplementedError()
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) = throw NotImplementedError()
    override suspend fun delete(recurring: Recurring) = throw NotImplementedError()
}

/**
 * A transaction as the ledger holds it: the money leg on an asset account plus the
 * counterpart leg that explains it — an EQUITY reconciliation for an adjustment.
 */
fun transaction(
    id: Long = 1L,
    amount: Double = 100.0,
    type: TransactionType = TransactionType.EXPENSE,
): Transaction {
    val cents = (amount * 100).toLong()
    val (moneyAmount, counterpart) = when (type) {
        TransactionType.EXPENSE -> -cents to Account(id = 10, name = "Food", type = AccountType.EXPENSE, currency = "BRL")
        TransactionType.INCOME -> cents to Account(id = 11, name = "Salary", type = AccountType.INCOME, currency = "BRL")
        TransactionType.ADJUSTMENT -> cents to Account(id = 12, name = "Reconciliation", type = AccountType.EQUITY, currency = "BRL")
    }

    return Transaction(
        id = id,
        title = "Op $id",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(account = Account(id = 1, name = "Account", type = AccountType.ASSET, currency = "BRL"), amount = moneyAmount),
            Entry(account = counterpart, amount = -moneyAmount),
        ),
    )
}
