package com.neoutils.finsight.domain.error

import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.resources.retire_error_has_budget
import com.neoutils.finsight.resources.retire_error_has_recurring
import com.neoutils.finsight.resources.retire_error_has_transactions
import com.neoutils.finsight.util.UiText

/**
 * Why a category cannot be deleted, so it is archived instead. The reasons are the
 * same guards account and card deletion carry, read through the category's own
 * dimension and its facade id — a category is not a chart-of-accounts row (design
 * D4), so it speaks its own retire language rather than an account's.
 */
enum class RetireError(val message: String) {

    /** Deleting would break the entries classified on the category's dimension. */
    HAS_TRANSACTIONS(message = "Cannot delete a category that has transactions"),

    /**
     * `budget_categories.categoryId` is CASCADE: deleting a budgeted category would
     * strip it from the budget silently. Refused so the loss is never created.
     */
    HAS_BUDGET(message = "Cannot delete a category a budget still uses"),

    /**
     * `recurring.categoryId` is SET_NULL: deleting would leave the template
     * uncategorized rather than failing. Refused so the orphan is never created.
     */
    HAS_RECURRING(message = "Cannot delete a category a recurring transaction still uses"),
}

fun RetireError.toUiText() = when (this) {
    RetireError.HAS_TRANSACTIONS -> UiText.Res(Res.string.retire_error_has_transactions)
    RetireError.HAS_BUDGET -> UiText.Res(Res.string.retire_error_has_budget)
    RetireError.HAS_RECURRING -> UiText.Res(Res.string.retire_error_has_recurring)
}

/**
 * A refused retire action has a reason the user can act on — "this category has
 * transactions", "a budget still uses it". Without it the sheet just did not close
 * and said nothing. One owner, so archive and delete cannot drift apart.
 */
fun Throwable.toRetireUiMessage(): UiText = when (this) {
    is RetireException -> error.toUiText()
    else -> UiText.Res(Res.string.retire_action_error_generic)
}
