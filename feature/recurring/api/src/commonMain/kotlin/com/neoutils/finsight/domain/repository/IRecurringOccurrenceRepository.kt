package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.YearMonth

/**
 * What the confirmed cycles of a month **actually posted**, per currency and per nature.
 *
 * Two figures rather than one signed figure: the natures are two separate readings of
 * the month and nothing above adds them, so there is no sum for a sign to be the effect
 * on. Each is a magnitude.
 */
data class RecurringSettledMoney(
    val expense: MoneyByCurrency,
    val income: MoneyByCurrency,
) {
    companion object {
        /** A month with no confirmed cycle at all. */
        val none = RecurringSettledMoney(MoneyByCurrency.zero, MoneyByCurrency.zero)
    }
}

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

    /**
     * The money the confirmed cycles of [month] wrote into the ledger, per currency.
     *
     * **Read from the ledger, never from the template.** Confirming a cycle lets the
     * user override the amount, the account, the card, the title and the category of
     * that cycle while the template stays as it was — so summing confirmed templates
     * would produce a number that never existed. What is summed here is what the
     * transactions the occurrences point at actually registered.
     *
     * **The month is the occurrence's, not the transaction's date** (design D7). The
     * rule forbidding a recurring transaction to change month is declared and mapped to
     * a string with nothing in the app producing it, so a confirmed transaction can
     * still be edited into another month; cutting by the occurrence is what keeps the
     * money summed and the cycles counted from disagreeing about which month a cycle
     * belongs to.
     *
     * It does not consult `transactions.recurringId`. That column is grouping metadata,
     * no ledger read looks at it, and the path from an occurrence to its transaction is
     * a real foreign key.
     *
     * The nature of each figure is the ledger's — which nominal account the money landed
     * on — and not the type declared on the template.
     */
    suspend fun settledIn(month: YearMonth): RecurringSettledMoney
}
