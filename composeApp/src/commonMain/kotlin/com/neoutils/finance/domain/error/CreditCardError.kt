package com.neoutils.finance.domain.error

import com.neoutils.finance.resources.Res
import com.neoutils.finance.resources.credit_card_error_already_exist_name
import com.neoutils.finance.resources.credit_card_error_empty_name
import com.neoutils.finance.resources.credit_card_error_invalid_closing_day
import com.neoutils.finance.resources.credit_card_error_invalid_due_day
import com.neoutils.finance.resources.credit_card_error_missing_closing_day
import com.neoutils.finance.resources.credit_card_error_missing_due_day
import com.neoutils.finance.resources.credit_card_error_negative_limit
import com.neoutils.finance.resources.credit_card_error_not_found
import com.neoutils.finance.util.UiText

enum class CreditCardError(val message: String) {
    EMPTY_NAME(message = "Credit card name is required"),
    ALREADY_EXIST_NAME(message = "Credit card name already exists"),
    NEGATIVE_LIMIT(message = "Credit card limit cannot be negative"),
    MISSING_CLOSING_DAY(message = "Closing day is required"),
    INVALID_CLOSING_DAY(message = "Closing day must be between 1 and 31"),
    MISSING_DUE_DAY(message = "Due day is required"),
    INVALID_DUE_DAY(message = "Due day must be between 1 and 31"),
    NOT_FOUND(message = "Credit card not found"),
}

fun CreditCardError.toUiText() = when (this) {
    CreditCardError.EMPTY_NAME -> UiText.Res(Res.string.credit_card_error_empty_name)
    CreditCardError.ALREADY_EXIST_NAME -> UiText.Res(Res.string.credit_card_error_already_exist_name)
    CreditCardError.NEGATIVE_LIMIT -> UiText.Res(Res.string.credit_card_error_negative_limit)
    CreditCardError.MISSING_CLOSING_DAY -> UiText.Res(Res.string.credit_card_error_missing_closing_day)
    CreditCardError.INVALID_CLOSING_DAY -> UiText.Res(Res.string.credit_card_error_invalid_closing_day)
    CreditCardError.MISSING_DUE_DAY -> UiText.Res(Res.string.credit_card_error_missing_due_day)
    CreditCardError.INVALID_DUE_DAY -> UiText.Res(Res.string.credit_card_error_invalid_due_day)
    CreditCardError.NOT_FOUND -> UiText.Res(Res.string.credit_card_error_not_found)
}