package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.CategoryIcon
import com.neoutils.finsight.util.Validation

data class AccountFormUiState(
    val name: String = "",
    val selectedIcon: CategoryIcon = CategoryIcon.DEFAULT,
    val validation: Map<AccountField, Validation> = mapOf(),
    val isDefault: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
)

enum class AccountField {
    NAME
}
