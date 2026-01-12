package com.neoutils.finance.ui.modal.creditCardForm

import com.neoutils.finance.domain.model.form.CreditCardForm

data class CreditCardFormUiState(
    val form: CreditCardForm = CreditCardForm(),
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
)
