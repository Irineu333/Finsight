package com.neoutils.finance.ui.modal.accountForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.usecase.CreateAccountUseCase
import com.neoutils.finance.domain.usecase.UpdateAccountUseCase
import com.neoutils.finance.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountFormViewModel(
    private val account: Account?,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val isEditMode = account != null

    private val name = MutableStateFlow(
        FieldForm(
            text = account?.name.orEmpty(),
            validation = if (isEditMode) Validation.Valid else Validation.Waiting
        )
    )

    private val isDefault = MutableStateFlow(
        account?.isDefault ?: false
    )

    val uiState = combine(name, isDefault) { name, isDefault ->
        AccountFormUiState(
            name = name,
            isDefault = isDefault,
            isEditMode = isEditMode,
            canSubmit = name.validation == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountFormUiState(
            name = name.value,
            isDefault = isDefault.value,
            isEditMode = isEditMode,
            canSubmit = name.value.validation == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
        )
    )

    fun onAction(action: AccountFormAction) {
        when (action) {
            is AccountFormAction.NameChanged -> changeName(action.name)
            is AccountFormAction.IsDefaultChanged -> {
                isDefault.value = action.isDefault
            }

            is AccountFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {
        name.update {
            it.copy(
                text = newName,
                validation = Validation.Validating,
            )
        }

        debounceManager(
            scope = viewModelScope,
            key = "validate_account_name",
        ) {
            name.update {
                it.copy(
                    validation = validateAccountName(
                        name = newName,
                        ignoreId = account?.id
                    )?.let { error ->
                        Validation.Error(error)
                    } ?: Validation.Valid,
                )
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        validateAccountName(
            name = name.value.text,
            ignoreId = account?.id
        )?.let {
            return@launch
        }

        if (account != null) {
            updateAccountUseCase(
                account = account,
                name = name.value.text,
                isDefault = isDefault.value
            ).onSuccess {
                modalManager.dismissAll()
            }.onFailure {
                // TODO: Show error message to user
            }
            return@launch
        }

        createAccountUseCase(
            name = name.value.text,
            isDefault = isDefault.value
        ).onSuccess {
            modalManager.dismiss()
        }.onFailure {
            // TODO: Show error message to user
        }
    }
}
