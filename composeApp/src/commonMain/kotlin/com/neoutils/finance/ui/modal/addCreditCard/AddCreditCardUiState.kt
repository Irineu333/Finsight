package com.neoutils.finance.ui.modal.addCreditCard

import com.neoutils.finance.domain.model.form.AddCreditForm

data class AddCreditCardUiState(
    val forms: AddCreditForm = AddCreditForm(),
    val canSubmit: Boolean = false,
)
