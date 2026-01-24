package com.neoutils.finance.ui.modal.accountForm

import com.neoutils.finance.util.Validation

data class AccountFormUiState(
    val name: String = "",
    val validation: Map<AccountField, Validation> = mapOf(),
    val isDefault: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
)

enum class AccountField {
    NAME
}