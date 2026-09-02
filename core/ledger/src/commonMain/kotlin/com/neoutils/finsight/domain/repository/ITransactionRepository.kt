package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
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

    /**
     * The transactions dated within [startDate]..[endDate], **both days included**,
     * newest first.
     *
     * The period is the cut, and it is the database's to make: a caller that wants one
     * month of a history filters what it asked for rather than what the user has ever
     * recorded, so what the answer costs is what the period holds. Their legs are read
     * in bulk beside them, so it costs no query per posting either.
     */
    suspend fun getTransactionsBetween(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction>

    suspend fun getTransactionById(id: Long): Transaction?

    /**
     * Which of [ids] still name a transaction — the identities alone, not the transactions.
     *
     * For a caller that only has to tell what is still there from what was removed, and has a
     * page of identities rather than one. Hydrating each of them costs a read of the row, a
     * read of its entries and a read of the chart of accounts, and answering existence does not
     * need any of the three; asked per identity it also makes the cost of a page grow with the
     * page. An identity absent from the result is a transaction that is gone.
     */
    suspend fun getExistingTransactionIds(ids: Collection<Long>): Set<Long>

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
     * [contra] has no default on purpose: a rewrite deletes the old entries, so a caller
     * that forgets it turns a one-sided intent into an unbalanced write — refused at the
     * boundary, with the edit silently rolled back. Defaulting it to `null` let exactly
     * that compile.
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

    /**
     * Removes [id], saying the [announcement] to
     * [com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude] on the way.
     *
     * Spelling it out is what this overload is for; the removals above are the same
     * removal with [RemovalAnnouncement.Announced], which is why forgetting the argument
     * cannot cost anybody the announcement.
     *
     * The default carries the answer no further, because an implementation with no prelude
     * has nothing to withhold: [beforeRemoval][com.neoutils.finsight.domain.ledger.TransactionRemovalPrelude.beforeRemoval]
     * is the only thing it governs, and where it is not spoken both answers are the same
     * removal.
     */
    suspend fun deleteTransactionById(id: Long, announcement: RemovalAnnouncement) =
        deleteTransactionById(id)

    /** [deleteTransactionsByIds], with the [announcement] spelled out — see above. */
    suspend fun deleteTransactionsByIds(ids: List<Long>, announcement: RemovalAnnouncement) =
        deleteTransactionsByIds(ids)
}
