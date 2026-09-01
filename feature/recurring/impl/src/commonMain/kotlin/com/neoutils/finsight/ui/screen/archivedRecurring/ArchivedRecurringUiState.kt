package com.neoutils.finsight.ui.screen.archivedRecurring

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.screen.recurring.RecurringRowUi
import com.neoutils.finsight.ui.screen.recurring.templateRowOf

/**
 * An archived recurring together with the figure it reads as, denominated by the account
 * or card it names.
 *
 * [amount] is `null` when nothing denominates it any more — the source was deleted, so
 * there is no currency the value could be read in. The row renders the unresolved mark in
 * its place rather than dropping the figure, exactly as it does in the monthly list.
 */
data class ArchivedRecurringUi(
    val recurring: Recurring,
    val amount: DisplayAmount?,
) {
    /**
     * What the row draws — the template reading, which is the only one an archived rule
     * has: it generates no cycle in any month, so there is never a fact to read instead.
     */
    val row: RecurringRowUi = templateRowOf(recurring, amount)
}

sealed interface ArchivedRecurringUiState {

    data object Loading : ArchivedRecurringUiState

    /** Nothing has ever been archived — the destination says so and offers nothing. */
    data object Empty : ArchivedRecurringUiState

    data class Content(
        val recurring: List<ArchivedRecurringUi>,
    ) : ArchivedRecurringUiState
}
