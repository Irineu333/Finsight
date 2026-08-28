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

    /**
     * The transactions [ids] names, in one query.
     *
     * It exists because the two reads beside it are both wrong for a list that starts
     * from a set of identities: [getTransactionById] per row is the N+1 a list pays on
     * every emission, and [getAllTransactions] reads the whole ledger to keep a handful
     * of rows. Whoever already holds the ids — a screen listing the transactions of
     * confirmed cycles, of an installment, of anything the ledger hands identities for —
     * asks for exactly those.
     *
     * An id with no row is an **absence, not an error**: the result holds the
     * transactions that exist, in no guaranteed correspondence with [ids]. Order is the
     * ledger's own — most recent first — and never the caller's.
     */
    suspend fun getTransactionsByIds(ids: Collection<Long>): List<Transaction>

    /** Writes the user's [intent] as a balanced set of ledger entries. */
    suspend fun createTransaction(intent: TransactionIntent): Transaction

    /**
     * Writes several intents as one unit. An installment is a single decision by
     * the user, so its N transactions must be all-or-nothing: writing 7 of 12 and
     * failing would leave an installment describing money that was never recorded.
     */
    suspend fun createTransactions(intents: List<TransactionIntent>): List<Transaction>

    /**
     * Rewrites the transaction's row and its ledger legs from [legs] and [contra].
     *
     * The rewrite deletes every old entry and rebuilds from the set given, which is
     * the same vocabulary [createTransaction] accepts: an operation with two monetary
     * legs — a transfer — states both, and the boundary completes and balances the
     * intent exactly as it does on creation, conversion legs included.
     *
     * [contra] has no default on purpose: a rewrite deletes the old entries, so a
     * caller that forgets it turns a one-sided intent into an unbalanced write —
     * refused at the boundary, with the edit silently rolled back. Defaulting it to
     * `null` let exactly that compile.
     */
    suspend fun updateTransaction(
        id: Long,
        title: String?,
        date: LocalDate,
        legs: List<TransactionLeg>,
        contra: ContraLeg?,
    )

    suspend fun deleteTransactionById(id: Long)

    /** Removes several transactions as one unit — see [createTransactions]. */
    suspend fun deleteTransactionsByIds(ids: List<Long>)
}
