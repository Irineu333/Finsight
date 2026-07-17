package com.neoutils.finsight.domain.repository

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Entry
import kotlinx.coroutines.flow.Flow
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * Reads derived from the double-entry ledger. Every figure is a sum of entry
 * amounts (debit-positive), so account balance, category spending, invoice owed
 * and net worth all share one mechanism — no `signedImpact()` or per-type sign.
 * Values are returned in the major currency unit (reais).
 */
/**
 * The per-account, per-period money flows (reais) an account screen shows, derived
 * from the ledger. [adjustment] is signed; the rest are positive magnitudes.
 */
data class AccountFlows(
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
)

interface IEntryRepository {

    /** The entries (legs) of an operation, each hydrated with its account. */
    suspend fun getEntriesByOperation(operationId: Long): List<Entry>

    /** Observes the entries (legs) of an operation, each hydrated with its account. */
    fun observeEntriesByOperation(operationId: Long): Flow<List<Entry>>

    /**
     * Natural balance of [accountId] (or of all ASSET accounts when null) up to
     * and including [target].
     */
    suspend fun balanceUpTo(target: YearMonth, accountId: Long? = null): Double

    /** Natural balance of [accountId] within [month] — used for category spending. */
    suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double

    /** The income/expense/adjustment/invoice-payment flows of [accountId] in [month]. */
    suspend fun accountFlows(month: YearMonth, accountId: Long): AccountFlows

    /** Number of ledger entries on a category (chart) account within [month]. */
    suspend fun entryCountInMonth(month: YearMonth, accountId: Long): Int

    /** Amount owed on an invoice (positive), from its liability-leg entries. */
    suspend fun invoiceOwed(invoiceId: Long): Double

    /** Net worth = Σ ASSET − Σ LIABILITY, via the same entry mechanism. */
    suspend fun netWorth(): Double

    /**
     * Natural balance (reais) per category account of [categoryType] in a date
     * range, counting only operations that also have a leg on one of
     * [siblingAccountIds] — i.e. spending/income "seen from" those accounts.
     */
    suspend fun categoryTotals(
        categoryType: AccountType,
        startDate: LocalDate,
        endDate: LocalDate,
        siblingAccountIds: List<Long>,
    ): Map<Long, Double>

    /** Natural balance (reais) per category account scoped to a set of invoices. */
    suspend fun categoryTotalsForInvoices(
        categoryType: AccountType,
        invoiceIds: List<Long>,
    ): Map<Long, Double>
}
