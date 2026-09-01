package com.neoutils.finsight.domain.model

import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth

/**
 * What a month has done with the cycle of one template — the four states, and the whole
 * vocabulary the recurring screen is organised by.
 *
 * **The state is the cycle's, never the template's.** A template has no month; only its
 * cycle in a month has one, and that is what makes these four derivable at all:
 * [POSTED] and [SKIPPED] are the status of the month's occurrence, and [PENDING] and
 * [UPCOMING] are the two halves of having no occurrence at all. Nothing here is
 * persisted.
 *
 * The declaration order is the order the sections are read in, and it is by **how much
 * each group asks of the user**: what is overdue and unresolved first, what is coming
 * next, and what the month has already settled last.
 */
enum class RecurringCycleStatus {
    /** No occurrence, and the cycle's date has come. */
    PENDING,

    /** No occurrence, and the cycle's date is still ahead. */
    UPCOMING,

    /** Confirmed: there is a transaction in the ledger for this cycle. */
    POSTED,

    /** Skipped: the month was answered, and no money moved. */
    SKIPPED,
}

/**
 * One template's cycle in one month, in the state that month put it in.
 *
 * [date] is the cycle's own date: the day the template declares, resolved onto the
 * month — with a month shorter than that day taking its last one — for a cycle with no
 * occurrence, and the date the occurrence was filed on for one that has it. It is what
 * orders a section, and for the two unhandled states it is also what splits them.
 *
 * [occurrence] is `null` exactly for [RecurringCycleStatus.PENDING] and
 * [RecurringCycleStatus.UPCOMING]: those are the states of having nothing recorded.
 */
data class RecurringCycle(
    val recurring: Recurring,
    val status: RecurringCycleStatus,
    val date: LocalDate,
    val occurrence: RecurringOccurrence? = null,
)

/**
 * A month of recurrings, partitioned — the single answer every consumer of "what state
 * is this cycle in" reads from.
 *
 * Every cycle is in exactly one group, and the groups are complementary by
 * construction rather than by four predicates that could disagree. A template with no
 * cycle in [month] — archived, or a month before its series began — is in none of them,
 * because it has no cycle to be in a state.
 *
 * Each group is ordered by [RecurringCycle.date], ascending.
 */
data class RecurringCycles(
    val month: YearMonth,
    val byStatus: Map<RecurringCycleStatus, List<RecurringCycle>>,
) {
    operator fun get(status: RecurringCycleStatus): List<RecurringCycle> =
        byStatus[status].orEmpty()

    val pending: List<RecurringCycle> get() = get(RecurringCycleStatus.PENDING)
    val upcoming: List<RecurringCycle> get() = get(RecurringCycleStatus.UPCOMING)
    val posted: List<RecurringCycle> get() = get(RecurringCycleStatus.POSTED)
    val skipped: List<RecurringCycle> get() = get(RecurringCycleStatus.SKIPPED)

    /**
     * The cycles the month may still ask for — the two states with no occurrence, which
     * are together exactly what has nothing recorded for it.
     *
     * The forecast of the month is drawn from this and not from a second walk over the
     * templates: whoever projects the money and whoever lists the rows must be looking
     * at the same set.
     */
    val unhandled: List<RecurringCycle> get() = pending + upcoming

    companion object {
        /** A month with no cycle at all. */
        fun none(month: YearMonth) = RecurringCycles(month, emptyMap())
    }
}
