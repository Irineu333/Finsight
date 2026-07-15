package com.neoutils.finsight.domain.repository

import kotlinx.datetime.YearMonth

/**
 * Reads derived from the double-entry ledger. Every figure is a sum of entry
 * amounts (debit-positive), so account balance, category spending, invoice owed
 * and net worth all share one mechanism — no `signedImpact()` or per-type sign.
 * Values are returned in the major currency unit (reais).
 */
interface IEntryRepository {

    /**
     * Natural balance of [accountId] (or of all ASSET accounts when null) up to
     * and including [target].
     */
    suspend fun balanceUpTo(target: YearMonth, accountId: Long? = null): Double

    /** Natural balance of [accountId] within [month] — used for category spending. */
    suspend fun balanceInMonth(month: YearMonth, accountId: Long): Double

    /** Amount owed on an invoice (positive), from its liability-leg entries. */
    suspend fun invoiceOwed(invoiceId: Long): Double

    /** Net worth = Σ ASSET − Σ LIABILITY, via the same entry mechanism. */
    suspend fun netWorth(): Double
}
