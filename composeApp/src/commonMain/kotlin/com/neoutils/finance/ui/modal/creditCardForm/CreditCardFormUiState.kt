package com.neoutils.finance.ui.modal.creditCardForm

import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.util.Validation

data class CreditCardFormUiState(
    val form: CreditCardForm = CreditCardForm(),
    val validation: Map<CreditCardField, Validation> = mapOf(),
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)

enum class CreditCardField {
    NAME,
    LIMIT,
    CLOSING_DAY,
    DUE_DAY,
}
