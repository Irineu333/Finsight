package com.neoutils.finsight.ui.modal

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
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

class FakeTransactionRepository : ITransactionRepository {

    private val byId = MutableSharedFlow<Transaction?>(replay = 1)

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
    override suspend fun getTransactionById(id: Long): Transaction? = throw NotImplementedError()
    override suspend fun createTransaction(intent: TransactionIntent): Transaction = throw NotImplementedError()
    override suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction> = throw NotImplementedError()
    override suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg, contra: ContraLeg?) = throw NotImplementedError()
    override suspend fun deleteTransactionsByIds(ids: List<Long>) = ids.forEach { deleteTransactionById(it) }

    override suspend fun deleteTransactionById(id: Long) = throw NotImplementedError()
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
        TransactionType.EXPENSE -> -cents to Account(id = 10, name = "Food", type = AccountType.EXPENSE)
        TransactionType.INCOME -> cents to Account(id = 11, name = "Salary", type = AccountType.INCOME)
        TransactionType.ADJUSTMENT -> cents to Account(id = 12, name = "Reconciliation", type = AccountType.EQUITY)
    }

    return Transaction(
        id = id,
        title = "Op $id",
        date = LocalDate(2026, 1, 1),
        entries = listOf(
            Entry(account = Account(id = 1, name = "Account", type = AccountType.ASSET), amount = moneyAmount),
            Entry(account = counterpart, amount = -moneyAmount),
        ),
    )
}
