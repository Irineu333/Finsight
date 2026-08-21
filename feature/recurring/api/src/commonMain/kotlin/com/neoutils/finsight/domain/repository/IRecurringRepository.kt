package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import kotlinx.coroutines.flow.Flow

interface IRecurringRepository {
    fun observeAllRecurring(): Flow<List<Recurring>>
    fun observeRecurringById(id: Long): Flow<Recurring?>

    /**
     * One template by identity. A transaction carries the id of the recurring that
     * produced it and nothing more (design D6), so the screen that renders the link
     * resolves it here.
     */
    suspend fun getRecurringById(id: Long): Recurring?

    /**
     * Whether any template — active or stopped — still points at this account or
     * card. Deleting one out from under a recurring leaves it with nothing to post
     * through, so the owning feature asks before removing it.
     */
    suspend fun hasRecurringForAccount(accountId: Long): Boolean
    suspend fun hasRecurringForCreditCard(creditCardId: Long): Boolean
    suspend fun hasRecurringForCategory(categoryId: Long): Boolean

    /**
     * Whether any transaction still carries this template's link — one of the two
     * guards that decide whether the recurring may be deleted or must be archived.
     */
    suspend fun hasTransactionForRecurring(recurringId: Long): Boolean
    /**
     * Writes a new template and answers the identity the store gave it.
     *
     * The identity is answered rather than dropped because a caller that cannot name what it
     * just created cannot report it either — and the surface that writes one from outside has
     * nothing else to point the requester at.
     */
    suspend fun insert(recurring: Recurring): Long

    /**
     * Creates a template and posts [firstCycle] as its cycle 1 — the template, the
     * transaction and the occurrence that records it as **one unit of work**.
     *
     * A template left behind by a refused transaction is a model the user never asked
     * for, and the pending list would offer its first cycle for confirmation moments
     * after the screen told them the write failed.
     *
     * [recurring] arrives with `id = 0`, and [firstCycle] and [occurrence] without the
     * `recurringId`, for the same reason
     * [IRecurringOccurrenceRepository.confirmCycle] takes an occurrence without a
     * `transactionId`: the identity only exists once the row is written, and this is
     * the only place that learns it.
     *
     * **No dispatcher switch may happen anywhere along this path.** Room's reentrancy
     * travels in a coroutine context element, and this call nests three writer
     * connections in one coroutine — the template's, the confirmation's and the
     * transaction's. A switch would take a second connection and deadlock instead of
     * emitting a `SAVEPOINT`.
     */
    suspend fun createWithFirstCycle(
        recurring: Recurring,
        firstCycle: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction
    suspend fun update(recurring: Recurring)
    suspend fun delete(recurring: Recurring)
}
