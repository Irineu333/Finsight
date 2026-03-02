package com.neoutils.finsight.domain.error

import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.recurring_error_account_required
import com.neoutils.finsight.resources.recurring_error_amount_required
import com.neoutils.finsight.resources.recurring_error_amount_zero
import com.neoutils.finsight.resources.recurring_error_invalid_day
import com.neoutils.finsight.resources.recurring_error_title_or_category_required
import com.neoutils.finsight.util.UiText

enum class RecurringError(val message: String) {
    AMOUNT_REQUIRED(message = "Amount is required."),
    AMOUNT_ZERO(message = "Amount cannot be zero."),
    TITLE_OR_CATEGORY_REQUIRED(message = "Title or category is required."),
    INVALID_DAY(message = "Day of month must be between 1 and 31."),
    ACCOUNT_REQUIRED(message = "Account is required."),
}

fun RecurringError.toUiText() = when (this) {
    RecurringError.AMOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_amount_required)
    RecurringError.AMOUNT_ZERO -> UiText.Res(Res.string.recurring_error_amount_zero)
    RecurringError.TITLE_OR_CATEGORY_REQUIRED -> UiText.Res(Res.string.recurring_error_title_or_category_required)
    RecurringError.INVALID_DAY -> UiText.Res(Res.string.recurring_error_invalid_day)
    RecurringError.ACCOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_account_required)
}
