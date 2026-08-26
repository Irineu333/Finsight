package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.datetime.YearMonth

/**
 * A recurring together with the figure it reads as, denominated by the account or card
 * it names (design D17).
 *
 * Paired, rather than a list of recurrings beside a map of currencies: carrying the
 * denomination apart from the value is precisely the drift [DisplayAmount] exists to
 * make impossible.
 *
 * [amount] is `null` when no account denominates the template — its source was deleted,
 * so there is no currency the value could be read in. The row does not drop the figure:
 * it renders the unresolved mark in its place and states the cause beside it, because in
 * a dense list an absence is invisible and a row that changes height explains nothing.
 */
data class RecurringItem(
    val recurring: Recurring,
    val amount: DisplayAmount?,
)

/**
 * The month above the list, in the four figures the screen shows and the counts that
 * account for what they cannot hold.
 *
 * Fact and forecast are two classes of thing and are labelled as such: money in the
 * ledger, and money the month may still ask for. Every figure arrives consolidated —
 * they span accounts, so they can span currencies — and each carries its own sign policy,
 * which here is a magnitude in all four: the card shows no total, so there is no sum for
 * a sign to be the effect on.
 */
data class RecurringMonthSummary(
    val settledExpense: ConsolidatedAmount,
    val settledIncome: ConsolidatedAmount,
    val forecastExpense: ConsolidatedAmount,
    val forecastIncome: ConsolidatedAmount,
    val handled: Int,
    val total: Int,
    val skipped: Int,
    val undenominated: Int,
) {
    /** Every figure the card draws — what the badge decides its own level from. */
    val figures: List<ConsolidatedAmount>
        get() = listOf(settledExpense, settledIncome, forecastExpense, forecastIncome)
}

sealed class RecurringUiState {

    abstract val filter: RecurringFilter

    data class Loading(
        override val filter: RecurringFilter = RecurringFilter.ACTIVE,
    ) : RecurringUiState()

    /**
     * The database holds no recurring at all — the only case that earns the big CTA
     * empty-state. A filter that merely happens to be empty is [Content] with an
     * empty list. [filter] survives so the FAB is still shown and knows its context.
     *
     * No summary here, and none is offered: with no template at all there is no month to
     * summarise, and the screen's whole job is the offer to create the first one.
     */
    data class Empty(
        override val filter: RecurringFilter,
    ) : RecurringUiState()

    /**
     * [selectedYearMonth] governs [summary] and nothing else; [filter] governs
     * [filteredRecurring] and nothing else. The inversion of `SummaryCard`'s grammar is
     * deliberate — there the card's chips govern the card *and* the list. A template has
     * no month, only its occurrence has one, so a month that narrowed the list would be
     * cutting by something the list's rows do not have.
     */
    data class Content(
        val filteredRecurring: List<RecurringItem>,
        override val filter: RecurringFilter,
        val selectedYearMonth: YearMonth,
        val summary: RecurringMonthSummary,
    ) : RecurringUiState()
}
