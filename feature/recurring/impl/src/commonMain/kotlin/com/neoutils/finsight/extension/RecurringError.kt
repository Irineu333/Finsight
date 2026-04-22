package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.feature.recurring.impl.resources.Res
import com.neoutils.finsight.feature.recurring.impl.resources.recurring_error_account_required
import com.neoutils.finsight.feature.recurring.impl.resources.recurring_error_amount_required
import com.neoutils.finsight.feature.recurring.impl.resources.recurring_error_amount_zero
import com.neoutils.finsight.feature.recurring.impl.resources.recurring_error_invalid_day
import com.neoutils.finsight.feature.recurring.impl.resources.recurring_error_title_or_category_required
import com.neoutils.finsight.util.UiText

fun RecurringError.toUiText() = when (this) {
    RecurringError.AMOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_amount_required)
    RecurringError.AMOUNT_ZERO -> UiText.Res(Res.string.recurring_error_amount_zero)
    RecurringError.TITLE_OR_CATEGORY_REQUIRED -> UiText.Res(Res.string.recurring_error_title_or_category_required)
    RecurringError.INVALID_DAY -> UiText.Res(Res.string.recurring_error_invalid_day)
    RecurringError.ACCOUNT_REQUIRED -> UiText.Res(Res.string.recurring_error_account_required)
}
