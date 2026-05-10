package com.neoutils.finsight.feature.accounts.modal.accountForm

import com.neoutils.finsight.feature.accounts.model.form.AccountForm
import com.neoutils.finsight.core.ui.util.Validation

sealed class AccountFormUiState {
    data object Loading : AccountFormUiState()

    data object Error : AccountFormUiState()

    data class Content(
        val form: AccountForm,
        val validation: Map<AccountField, Validation>,
        val isEditMode: Boolean,
        val canSubmit: Boolean,
        val canChangeDefault: Boolean,
    ) : AccountFormUiState()
}

enum class AccountField {
    NAME
}
