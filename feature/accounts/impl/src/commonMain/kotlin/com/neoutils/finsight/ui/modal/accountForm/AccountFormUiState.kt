package com.neoutils.finsight.ui.modal.accountForm

import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.Validation

data class AccountFormUiState(
    val name: String = "",
    val selectedIcon: AppIcon = AppIcon.WALLET,
    val validation: Map<AccountField, Validation> = mapOf(),
    val isDefault: Boolean = false,
    val isEditMode: Boolean = false,
    val canSubmit: Boolean = false,
    val canChangeDefault: Boolean = true,
    /**
     * The currency of the account — the one shown, and on creation the one chosen.
     *
     * No default: which currency a new account is denominated in is a decision someone makes,
     * and a default here would be the silent one the model gave up.
     */
    val currency: String,
    /**
     * Decided by the **mode of the form** and not by the state of the account (design D12):
     * a selector when creating, a locked state line when editing, always. The currency is an
     * attribute of identity, so nothing about the account — not even having no entries — makes
     * it changeable.
     */
    val canChangeCurrency: Boolean,
)

enum class AccountField {
    NAME
}
