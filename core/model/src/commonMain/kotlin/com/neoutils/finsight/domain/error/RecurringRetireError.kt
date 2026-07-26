package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_retire_error_has_budget
import com.neoutils.finsight.resources.recurring_retire_error_has_transactions
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText

/**
 * Why a recurring cannot be deleted, so it is archived instead.
 *
 * Its own enum, beside — not instead of — [RetireError]: the reasons do not overlap.
 * `RetireError`'s are justified by the category's foreign keys and none of them can
 * happen to a recurring; these two cannot happen to a category. A shared enum would
 * hand every `when` members that are impossible in its branch (design D4).
 */
enum class RecurringRetireError(val message: String) {

    /**
     * Transactions carry this template's link. Deleting would not orphan any ledger
     * entry — nothing in the ledger references a recurring — but it would destroy the
     * link between the entries it generated and the template that originated them,
     * along with the record of the cycles already handled.
     */
    HAS_TRANSACTIONS(message = "Cannot delete a recurring that has generated transactions"),

    /**
     * `budgets` declares no foreign key at all, so nothing else would catch this:
     * deleting would leave a budget pointing at an id that no longer exists, and its
     * percentage limit silently read as zero.
     */
    HAS_BUDGET(message = "Cannot delete a recurring a budget still uses as its base income"),
}

fun RecurringRetireError.toUiText() = when (this) {
    RecurringRetireError.HAS_TRANSACTIONS ->
        UiText.Res(Res.string.recurring_retire_error_has_transactions)

    RecurringRetireError.HAS_BUDGET ->
        UiText.Res(Res.string.recurring_retire_error_has_budget)
}

/**
 * A refused retire action has a reason the user can act on. Without it the sheet just
 * did not close and said nothing. One owner, so archive and delete cannot drift apart.
 */
fun Throwable.toRecurringRetireUiMessage(): UiText = when (this) {
    is RecurringRetireException -> error.toUiText()
    else -> UiText.Res(Res.string.retire_action_error_generic)
}
