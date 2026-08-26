@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.model

import com.neoutils.finsight.extension.displayTitleOrNull
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

data class Recurring(
    val id: Long = 0,
    val type: TransactionType,
    val amount: Double,
    val title: String?,
    val dayOfMonth: Int,
    val category: Category?,
    val account: Account?,
    val creditCard: CreditCard?,
    val createdAt: Long,
    val isArchived: Boolean = false,
) {
    /**
     * What the template is called: its own title, or the name of its category.
     *
     * There is no third link to invent, and none is needed: `RecurringForm.toRecurring`
     * is the single owner of "a template has a title or a category", every write goes
     * through it, and archiving only copies a flag onto a row that already passed. So the
     * absence is asserted rather than papered over with a name the user never chose — the
     * day a new write path skips that owner, this is what says so, instead of a screen
     * quietly reading wrong.
     */
    val label
        get() = displayTitleOrNull(title, category)
            ?: error("A recurring has a title or a category (RecurringForm.toRecurring).")

    /**
     * The month the series begins in — its **cycle 1**.
     *
     * `createdAt` is the anchor the cycle numbering is counted from (`RecurringForm.toRecurring`),
     * and this is that anchor read as a month, so that whoever asks "does this template have a
     * cycle in month X" and whoever numbers that cycle answer from the same expression. A
     * template born out of a past transaction is anchored on the transaction's date, so its
     * origin is that month and not the day it was typed.
     *
     * Before it there is nothing: no cycle to confirm, to skip, or to be waiting for.
     */
    val originMonth: YearMonth
        get() = Instant.fromEpochMilliseconds(createdAt).toYearMonth()

    /**
     * Whether this template has a cycle in [month] **at all** — before asking whether
     * anything was recorded for it.
     *
     * Two conditions, and they fail in opposite directions of time. The series has begun
     * ([originMonth] is cycle 1, and there is no cycle 0); and it has not been archived,
     * which stops it generating cycles in any month, past ones included — archiving is a
     * statement about the template, not about a date.
     *
     * It is one member because the two callers that need it must not answer differently:
     * the projection of a month and the counter of that same month are the same set,
     * seen once as money and once as a count.
     */
    fun generatesCycleIn(month: YearMonth): Boolean = !isArchived && originMonth <= month

    /**
     * Which cycle of the series [month] is — 1 for [originMonth], and `null` for a month
     * before it, where the series has no cycle to number.
     *
     * The nullable answer is the whole point. The numbering used to be
     * `origin.monthsUntil(month) + 1` written out at each site that needed it, and
     * `monthsUntil` is a subtraction with no floor: a month before the origin produced
     * cycle 0, then −1, then −2. Those were persisted — into `recurring_occurrences` and
     * into the `recurringCycle` of the transaction — and read straight back onto the
     * screen, where a detail line said "Aluguel • 0". An ordinal that can be zero is not
     * an ordinal.
     *
     * Archiving is deliberately not part of it. A template that stopped generating cycles
     * still *had* the ones it generated, and numbering one is a different question from
     * offering it — which is what [generatesCycleIn] answers.
     */
    fun cycleNumberIn(month: YearMonth): Int? =
        (originMonth.monthsUntil(month) + 1).takeIf { it >= 1 }

    /**
     * Whether the money still has somewhere to move through.
     *
     * False when the account or card was deleted (the reference is gone) or
     * archived (it exists, but receives nothing new). The template survives
     * either way — it just cannot be posted until the user points it somewhere
     * real, which is why this is derived and never persisted.
     *
     * [category] is deliberately not part of it: "uncategorized" is a legitimate
     * ledger state, backed by a system account. An accountless transaction is not.
     */
    val hasUsableSource: Boolean
        get() = creditCard?.isArchived == false || account?.isArchived == false
}
