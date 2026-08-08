package com.neoutils.finsight.ui.screen.recurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.DisplayAmount

/**
 * A recurring together with the figure it reads as, denominated by the account or card
 * it names (design D17).
 *
 * Paired, rather than a list of recurrings beside a map of currencies: carrying the
 * denomination apart from the value is precisely the drift [DisplayAmount] exists to
 * make impossible.
 *
 * [amount] is `null` when the account the template named has gone missing: the card
 * still says what it is, and the figure is simply not shown, because a number nobody
 * can denominate is not one the user should be shown in some other currency.
 */
data class RecurringItem(
    val recurring: Recurring,
    val amount: DisplayAmount?,
)

sealed class RecurringUiState {

    abstract val filter: RecurringFilter

    data class Loading(
        override val filter: RecurringFilter = RecurringFilter.ACTIVE,
    ) : RecurringUiState()

    /**
     * The database holds no recurring at all — the only case that earns the big CTA
     * empty-state. A filter that merely happens to be empty is [Content] with an
     * empty list. [filter] survives so the FAB is still shown and knows its context.
     */
    data class Empty(
        override val filter: RecurringFilter,
    ) : RecurringUiState()

    data class Content(
        val filteredRecurring: List<RecurringItem>,
        override val filter: RecurringFilter,
    ) : RecurringUiState()
}
