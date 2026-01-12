package com.neoutils.finance.ui.modal.editCreditCard

import com.neoutils.finance.domain.model.form.CreditCardForm

data class EditCreditCardUiState(
    val form: CreditCardForm = CreditCardForm(),
    val canSubmit: Boolean = false,
)
