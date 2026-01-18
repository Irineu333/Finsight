package com.neoutils.finance.ui.modal.accountForm

import com.neoutils.finance.util.FieldForm

data class AccountFormUiState(
    val name: FieldForm = FieldForm(),
    val isDefault: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
)
