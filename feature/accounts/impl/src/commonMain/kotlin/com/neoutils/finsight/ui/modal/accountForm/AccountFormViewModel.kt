package com.neoutils.finsight.ui.modal.accountForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.EditAccount
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.UpdateAccountUseCase
import com.neoutils.finsight.domain.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ObservableMutableMap
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AccountFormViewModel(
    private val account: Account?,
    private val validateAccountName: ValidateAccountNameUseCase,
    /**
     * Read once, to pre-select the currency of an account being created — a decision, not a
     * figure, which is why a snapshot is the right shape here.
     */
    baseCurrencyRepository: IBaseCurrencyRepository,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = account != null

    private val name = MutableStateFlow(account?.name.orEmpty())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(account?.iconKey ?: AppIcon.WALLET.key))

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            if (isEditMode) {
                AccountField.NAME to Validation.Valid
            } else {
                AccountField.NAME to Validation.Waiting
            }
        )
    )

    private val isDefault = MutableStateFlow(account?.isDefault ?: false)

    // Pre-selected with the base on creation; on edit it is the account's own, and no action
    // moves it (design D12).
    private val currency = MutableStateFlow(account?.currency ?: baseCurrencyRepository.current())

    val uiState = combine(name, selectedIcon, isDefault, validation, currency) { name, selectedIcon, isDefault, validation, currency ->
        AccountFormUiState(
            name = name,
            selectedIcon = selectedIcon,
            validation = validation,
            isDefault = isDefault,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
            currency = currency,
            canChangeCurrency = !isEditMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountFormUiState(
            name = name.value,
            selectedIcon = selectedIcon.value,
            validation = validation,
            isDefault = isDefault.value,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
            currency = currency.value,
            canChangeCurrency = !isEditMode,
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

            is AccountFormAction.IconSelected -> {
                selectedIcon.value = action.icon
            }

            is AccountFormAction.CurrencySelected -> {
                // Guarded rather than trusted: editing renders no control that emits this,
                // and the domain refuses the change besides (design D12).
                if (!isEditMode) currency.value = action.currency
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
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
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
                accountId = account.id,
            ) {
                it.copy(
                    name = name,
                    iconKey = selectedIcon.value.key,
                    isDefault = isDefault.value
                )
            }.onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(EditAccount(isDefault.value))
                modalManager.dismissAll()
            }
            return@launch
        }

        createAccountUseCase(
            name = name,
            isDefault = isDefault.value,
            iconKey = selectedIcon.value.key,
            currency = currency.value,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(CreateAccount(isDefault.value))
            modalManager.dismiss()
        }
    }
}
