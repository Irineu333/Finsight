package com.neoutils.finance.ui.modal.accountForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.usecase.CreateAccountUseCase
import com.neoutils.finance.domain.usecase.UpdateAccountUseCase
import com.neoutils.finance.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.Validation
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.ObservableMutableMap
import com.neoutils.finance.util.validation
import kotlinx.coroutines.flow.*
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

    private val name = MutableStateFlow(account?.name.orEmpty())

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            if (isEditMode) {
                AccountField.NAME to Validation.Valid
            } else {
                AccountField.NAME to Validation.Waiting
            }
        )
    )

    private val isDefault = MutableStateFlow(
        account?.isDefault ?: false
    )

    val uiState = combine(name, isDefault, validation) { name, isDefault, validation ->
        AccountFormUiState(
            name = name,
            validation = validation,
            isDefault = isDefault,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountFormUiState(
            name = name.value,
            validation = validation,
            isDefault = isDefault.value,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
        )
    )

    fun onAction(action: AccountFormAction) {
        when (action) {
            is AccountFormAction.NameChanged -> {
                changeName(action.name)
            }

            is AccountFormAction.IsDefaultChanged -> {
                isDefault.value = action.isDefault
            }

            is AccountFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {

        validation[AccountField.NAME] = Validation.Validating

        name.value = newName

        debounceManager(
            scope = viewModelScope,
            key = "validate_account_name",
        ) {
            validation[AccountField.NAME] = validateAccountName(
                name = newName,
                ignoreId = account?.id
            ).validation
        }
    }

    private fun submit() = viewModelScope.launch {

        val name = validateAccountName(
            name = name.value,
            ignoreId = account?.id
        ).getOrElse {
            return@launch
        }

        if (account != null) {
            updateAccountUseCase(
                account = account,
                name = name,
                isDefault = isDefault.value
            ).onSuccess {
                modalManager.dismissAll()
            }.onFailure {
                // TODO: Show error message to user
            }
            return@launch
        }

        createAccountUseCase(
            name = name,
            isDefault = isDefault.value
        ).onSuccess {
            modalManager.dismiss()
        }.onFailure {
            // TODO: Show error message to user
        }
    }
}
