package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.TransactionUi
import kotlinx.datetime.YearMonth

/**
 * A cycle as the list renders it — in the two shapes a cycle can be read in.
 *
 * The split is not decoration: a cycle with nothing recorded for it can only be
 * described by the template that projects it, and a cycle that was posted is described
 * by the ledger. Which of the two a row is comes from the section it is in, so no row
 * has to say it for itself.
 */
sealed class RecurringCycleUi {

    /** The template the cycle belongs to — what every row leads to when tapped. */
    abstract val recurring: Recurring

    /**
     * A cycle read from the **template**: pending, upcoming, or skipped. None of the
     * three has a fact to read, and the figure is what the template says the month
     * would ask for.
     *
     * [amount] is `null` when no account denominates the template — its source was
     * deleted, so there is no currency the value could be read in. The row does not
     * drop the figure: it renders the unresolved mark in its place and states the cause
     * beside it, because in a dense list an absence is invisible and a row that changes
     * height explains nothing.
     */
    data class Template(
        override val recurring: Recurring,
        val amount: DisplayAmount?,
    ) : RecurringCycleUi()

    /**
     * A cycle read from the **ledger**: what the transaction of that cycle registered —
     * its figure, its identity and its classification — and not what the template
     * predicted.
     *
     * Confirming a cycle may override every one of those for that month alone, so a row
     * that read the template would assert about the month a number and a name that may
     * never have existed.
     */
    data class Posted(
        override val recurring: Recurring,
        val transaction: TransactionUi,
    ) : RecurringCycleUi()
}

/**
 * One state of cycle, and the cycles in it.
 *
 * A section only exists when it has cycles: an empty one would be the month summary
 * asserting an absence it already asserts, with less precision.
 */
data class RecurringSection(
    val status: RecurringCycleStatus,
    val cycles: List<RecurringCycleUi>,
)

/**
 * The month above the list, in the four figures the screen shows.
 *
 * Fact and forecast are two classes of thing and are labelled as such: money in the
 * ledger, and money the month may still ask for. Every figure arrives consolidated —
 * they span accounts, so they can span currencies — and each carries its own sign policy,
 * which here is a magnitude in all four: the card shows no total, so there is no sum for
 * a sign to be the effect on.
 *
 * [undenominated] is the one count the card still carries, and it is not a count of
 * cycles: no section of the list accounts for it, and it speaks of a failure — a template
 * pointing at an account that no longer exists — whose way out is pointing it somewhere
 * else.
 */
data class RecurringMonthSummary(
    val settledExpense: ConsolidatedAmount,
    val settledIncome: ConsolidatedAmount,
    val forecastExpense: ConsolidatedAmount,
    val forecastIncome: ConsolidatedAmount,
    val undenominated: Int,
) {
    /** Every figure the card draws — what the badge decides its own level from. */
    val figures: List<ConsolidatedAmount>
        get() = listOf(settledExpense, settledIncome, forecastExpense, forecastIncome)
}

sealed class RecurringUiState {

    abstract val filter: RecurringFilter

    data class Loading(
        override val filter: RecurringFilter = RecurringFilter.ALL,
    ) : RecurringUiState()

    /**
     * The database holds no recurring at all — the only case that earns the big CTA
     * empty-state. A month that merely happens to have no cycle is [Content] with no
     * section. [filter] survives so the FAB is still shown and knows its context.
     *
     * No summary here, and none is offered: with no template at all there is no month to
     * summarise, and the screen's whole job is the offer to create the first one.
     */
    data class Empty(
        override val filter: RecurringFilter,
    ) : RecurringUiState()

    /**
     * [selectedYearMonth] governs both halves of the screen. What [sections] lists are
     * the **cycles** of the month, and a cycle has a month by definition — the summary
     * and the list answer the same question about the same month, and a selector that
     * moved only one of them would be indistinguishable from a defect.
     *
     * [filter] governs [sections] and nothing else: under a cut by nature the summary
     * would have to suppress one of the two lines of each block, changing *shape* while
     * the list changes *content*.
     *
     * [sections] arrives ordered — pending, upcoming, posted, skipped — and holds no
     * empty section.
     */
    data class Content(
        val sections: List<RecurringSection>,
        override val filter: RecurringFilter,
        val selectedYearMonth: YearMonth,
        val summary: RecurringMonthSummary,
    ) : RecurringUiState()
}
