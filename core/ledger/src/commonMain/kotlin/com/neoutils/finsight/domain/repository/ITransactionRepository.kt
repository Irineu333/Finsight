package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionLeg
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate

interface ITransactionRepository {
    fun observeAllTransactions(): Flow<List<Transaction>>

    fun observeTransactionsBy(
        date: LocalDate? = null,
        dimensionId: Long? = null,
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

    /**
     * Rewrites the transaction's row and its ledger legs from [leg] and its [contra].
     *
     * ⚠️ Takes a **single** leg: the rewrite deletes every old entry and rebuilds from
     * this one plus the contra. That is only correct for a transaction with exactly one
     * monetary leg — an expense or an income. A transfer or a card payment has two, and
     * routing one through here would drop the second silently. What the rewrite can
     * express is decided by `Transaction.editObstacle`, the single owner both the screen
     * and `UpdateTransactionUseCase` read: the screen to decide whether to offer the
     * edit, the use case to refuse it. Any future support for editing those must change
     * this shape.
     *
     * [contra] has no default on purpose: a rewrite deletes the old entries, so a caller
     * that forgets it turns a one-sided intent into an unbalanced write — refused at the
     * boundary, with the edit silently rolled back. Defaulting it to `null` let exactly
     * that compile.
     */
    suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        leg: TransactionLeg,
        contra: ContraLeg?,
    )

    suspend fun deleteTransactionById(id: Long)

    /** Removes several transactions as one unit — see [createTransactions]. */
    suspend fun deleteTransactionsByIds(ids: List<Long>)
}
