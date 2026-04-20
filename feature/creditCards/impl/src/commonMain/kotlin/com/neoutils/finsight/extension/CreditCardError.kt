package com.neoutils.finsight.extension

import com.neoutils.finsight.domain.error.CreditCardError
import com.neoutils.finsight.feature.creditCards.impl.resources.Res
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_already_exist_name
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_empty_name
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_invalid_closing_day
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_invalid_due_day
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_missing_closing_day
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_missing_due_day
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_negative_limit
import com.neoutils.finsight.feature.creditCards.impl.resources.credit_card_error_not_found
import com.neoutils.finsight.util.UiText

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
