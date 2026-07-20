package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITransactionRepository {
    fun observeAllTransactions(): Flow<List<Transaction>>

    fun observeTransactionsBy(
        date: LocalDate? = null,
        invoiceId: Long? = null,
        creditCardId: Long? = null,
        accountId: Long? = null,
    ): Flow<List<Transaction>>

    fun observeTransactionById(id: Long): Flow<Transaction?>

    suspend fun getAllTransactions(): List<Transaction>
    suspend fun getTransactionById(id: Long): Transaction?

    /** Writes the user's [intent] as a balanced set of ledger entries. */
    suspend fun createTransaction(intent: TransactionIntent): Transaction

    /**
     * Writes several intents as one unit. An installment is a single decision by
     * the user, so its N transactions must be all-or-nothing: writing 7 of 12 and
     * failing would leave an installment describing money that was never recorded.
     */
    suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction>

    /** Rewrites the transaction's row and its ledger legs from the edited [leg]. */
    suspend fun updateTransaction(id: Long, title: String?, date: LocalDate, leg: TransactionLeg)

    suspend fun deleteTransactionById(id: Long)

    /** Removes several transactions as one unit — see [createTransactions]. */
    suspend fun deleteTransactionsByIds(ids: List<Long>)
}
