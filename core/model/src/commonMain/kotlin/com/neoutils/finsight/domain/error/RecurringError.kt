package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_error_account_required
import com.neoutils.finsight.resources.recurring_error_amount_not_positive
import com.neoutils.finsight.resources.recurring_error_amount_required
import com.neoutils.finsight.resources.recurring_error_category_direction_mismatch
import com.neoutils.finsight.resources.recurring_error_currency_mismatch
import com.neoutils.finsight.resources.recurring_error_invalid_day
import com.neoutils.finsight.resources.recurring_error_not_found
import com.neoutils.finsight.resources.recurring_error_title_or_category_required
import com.neoutils.finsight.util.UiText

enum class RecurringError(val message: String) {

    /**
     * The identity the operation was given matches no recurring.
     *
     * The use cases resolve the template when the operation runs rather than trusting
     * what the caller holds, so this is the answer whenever that resolution finds
     * nothing — and it is given before anything is written.
     */
    NOT_FOUND(message = "Recurring not found."),

    AMOUNT_REQUIRED(message = "Amount is required."),
    AMOUNT_NOT_POSITIVE(message = "Amount must be greater than zero."),
    TITLE_OR_CATEGORY_REQUIRED(message = "Title or category is required."),
    INVALID_DAY(message = "Day of month must be between 1 and 31."),
    ACCOUNT_REQUIRED(message = "Account is required."),

    /**
     * The confirmation was pointed at an account or card of a different currency from
     * the one the template's amount is denominated in.
     *
     * **Refused, not converted** (design D17). Converting would mean choosing a rate on
     * the user's behalf in the middle of a confirmation — a decision they did not ask
     * for and cannot see. It is also the one case that produced silently wrong data
     * *outside* the ledger: the raw number would be written down as if it were the other
     * currency.
     *
     * The selector is supposed to make this unreachable by offering only accounts of the
     * template's currency. This is the net, never the designed path.
     */
    CURRENCY_MISMATCH(message = "The target account is in a different currency from the recurring."),

    /**
     * The cycle was classified under a category that does not classify the direction the
     * money moved in — an expense confirmed under an income category, or the reverse.
     *
     * A category classifies one direction only (`isAccept`), and the nature of the contra
     * leg is taken *from the category*: the disagreement does not fail to balance, it
     * posts the cycle on the opposite nominal. The money leaves the account and the
     * posting reads back as income, with `Σ = 0` intact and nothing to notice.
     *
     * A confirmation is the one write of the app that reaches the ledger without a form,
     * so the refusal lives in the use case: there is no drop-what-the-direction-cannot-
     * carry step between the caller and the posting. It answers for a declared category
     * and for a template whose own is incoherent alike, since both reach the ledger the
     * same way.
     */
    CATEGORY_DIRECTION_MISMATCH(
        message = "The category classifies the opposite direction from the recurring.",
    ),
}

fun RecurringError.toUiText() = when (this) {
    RecurringError.NOT_FOUND -> UiText.Res(Res.string.recurring_error_not_found)
    RecurringError.AMOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_amount_required)
    RecurringError.AMOUNT_NOT_POSITIVE -> UiText.Res(Res.string.recurring_error_amount_not_positive)
    RecurringError.TITLE_OR_CATEGORY_REQUIRED -> UiText.Res(Res.string.recurring_error_title_or_category_required)
    RecurringError.INVALID_DAY -> UiText.Res(Res.string.recurring_error_invalid_day)
    RecurringError.ACCOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_account_required)
    RecurringError.CURRENCY_MISMATCH -> UiText.Res(Res.string.recurring_error_currency_mismatch)
    RecurringError.CATEGORY_DIRECTION_MISMATCH ->
        UiText.Res(Res.string.recurring_error_category_direction_mismatch)
}
