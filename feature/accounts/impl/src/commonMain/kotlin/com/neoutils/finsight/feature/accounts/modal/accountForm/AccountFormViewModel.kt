package com.neoutils.finsight.feature.accounts.modal.accountForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.accounts.error.AccountError
import com.neoutils.finsight.feature.accounts.event.CreateAccount
import com.neoutils.finsight.feature.accounts.event.EditAccount
import com.neoutils.finsight.feature.accounts.exception.AccountException
import com.neoutils.finsight.feature.accounts.extension.toUiText
import com.neoutils.finsight.feature.accounts.model.form.AccountForm
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.accounts.usecase.CreateAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.UpdateAccountUseCase
import com.neoutils.finsight.feature.accounts.usecase.ValidateAccountNameUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.ui.util.AppIcon
import com.neoutils.finsight.core.ui.util.Validation
import com.neoutils.finsight.core.utils.util.DebounceManager
import com.neoutils.finsight.core.utils.util.ObservableMutableMap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AccountFormViewModel(
    private val accountId: Long?,
    private val accountRepository: IAccountRepository,
    private val validateAccountName: ValidateAccountNameUseCase,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = accountId != null

    private val account = flow {
        emit(
            accountId?.let {
                accountRepository.getAccountById(accountId)
            }
        )
    }

    private val validation = ObservableMutableMap(
        map = mutableMapOf(
            AccountField.NAME to if (isEditMode) {
                Validation.Valid
            } else {
                Validation.Waiting
            }
        )
    )

    private val form = MutableStateFlow<AccountForm?>(null)

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {

        if (accountId == null) {
            form.value = AccountForm()

            return@launch
        }

        val account = accountRepository.getAccountById(accountId)

        if (account == null) {
            crashlytics.recordException(AccountException(AccountError.NOT_FOUND))
            modalManager.dismiss()
            return@launch
        }

        form.value = AccountForm(
            id = account.id,
            name = account.name,
            icon = AppIcon.fromKey(account.iconKey),
            isDefault = account.isDefault,
            createdAt = account.createdAt,
        )
    }

    val uiState = combine(
        form.filterNotNull(),
        account,
        validation,
    ) { form, account, validation ->
        AccountFormUiState.Content(
            form = form,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = account?.isDefault != true,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountFormUiState.Loading,
    )

    fun onAction(action: AccountFormAction) {
        when (action) {
            is AccountFormAction.NameChanged -> {
                changeName(action.name)
            }

            is AccountFormAction.IsDefaultChanged -> {
                form.update { it?.copy(isDefault = action.isDefault) }
            }

            is AccountFormAction.IconSelected -> {
                form.update { it?.copy(icon = action.icon) }
            }

            is AccountFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {

        form.update { it?.copy(name = newName) }

        validation[AccountField.NAME] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_account_name",
        ) {
            validation[AccountField.NAME] = validateAccountName(
                name = newName,
                ignoreId = accountId
            ).map {
                Validation.Valid
            }.getOrElse {
                Validation.Error(it.toUiText())
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        val form = form.value ?: return@launch

        val validatedName = validateAccountName(
            name = form.name,
            ignoreId = accountId
        ).getOrElse {
            return@launch
        }

        if (accountId != null) {
            updateAccountUseCase(
                accountId = accountId,
            ) {
                it.copy(
                    name = validatedName,
                    iconKey = form.icon.key,
                    isDefault = form.isDefault
                )
            }.onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(EditAccount(form.isDefault))
                modalManager.dismissAll()
            }
            return@launch
        }

        createAccountUseCase(
            name = validatedName,
            isDefault = form.isDefault,
            iconKey = form.icon.key,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(CreateAccount(form.isDefault))
            modalManager.dismiss()
        }
    }
}
