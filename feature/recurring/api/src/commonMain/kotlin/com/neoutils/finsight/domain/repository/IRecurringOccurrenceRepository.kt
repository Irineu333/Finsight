package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

interface IRecurringOccurrenceRepository {
    fun observeAllOccurrences(): Flow<List<RecurringOccurrence>>
    suspend fun getAllOccurrences(): List<RecurringOccurrence>
    suspend fun getOccurrenceBy(recurringId: Long, yearMonth: YearMonth): RecurringOccurrence?
    suspend fun getOccurrenceBy(recurringId: Long, cycleNumber: Int): RecurringOccurrence?
    suspend fun save(occurrence: RecurringOccurrence): Long

    /**
     * Writes the transaction of a confirmed cycle and the occurrence that records
     * it as **one unit of work**: either both persist or neither does.
     *
     * The two used to be written separately, and the gap between them was reachable:
     * a transaction without its occurrence makes the month show up as pending again,
     * and the re-entry check — which reads the occurrence — finds nothing to refuse,
     * so a second confirmation writes a **duplicate** into the ledger.
     *
     * The re-entry check lives inside this unit for the same reason. Reading it
     * outside is a TOCTOU, and the unique `(recurringId, yearMonth)` index does not
     * catch it because [save] is an upsert: with a row already there it updates,
     * silently overwriting instead of refusing.
     *
     * [occurrence] arrives without `transactionId` — it only exists once the
     * transaction is written — and the created [Transaction] is returned, because
     * that is what confirming a cycle produces.
     */
    suspend fun confirmCycle(
        intent: TransactionIntent,
        occurrence: RecurringOccurrence,
    ): Transaction
}
