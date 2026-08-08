package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class AccountFormUiState(
    val name: String = "",
    val selectedIcon: AppIcon = AppIcon.WALLET,
    val validation: Map<AccountField, Validation> = mapOf(),
    val isDefault: Boolean = false,
    // Affordance only: it decides whether the account offers the yield line and the
    // launch path, and enters no sum anywhere (design D2).
    val yieldsInterest: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
)

enum class AccountField {
    NAME
}
