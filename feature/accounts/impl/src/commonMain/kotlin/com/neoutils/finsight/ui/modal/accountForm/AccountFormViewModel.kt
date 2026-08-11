package com.neoutils.finsight.ui.modal.accountForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.ICurrencyRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.EditAccount
import com.neoutils.finsight.domain.usecase.CreateAccountUseCase
import com.neoutils.finsight.domain.usecase.EnsureYieldCategoryUseCase
import com.neoutils.finsight.domain.usecase.SuggestAccountIconUseCase
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
    // Which icon a new account opens on is a derivation over the icons already in
    // use, and it has a single owner in the domain — the form consumes it and
    // reimplements neither the criterion nor the order of preference.
    private val suggestAccountIcon: SuggestAccountIconUseCase,
    // The base currency is a **pre-selection** here and denominates nothing: it answers
    // "which currency is this new account most likely in", exactly as it does for the
    // account a fresh install starts with. What the account is actually denominated in
    // is whatever the user leaves in the row, and after that it never changes (D12).
    baseCurrencyRepository: IBaseCurrencyRepository,
    // The currencies a form may offer are stored data now, read from the single source
    // that holds them — the archived ones excluded, because archiving answers exactly
    // "stop offering me this".
    currencyRepository: ICurrencyRepository,
    private val createAccountUseCase: CreateAccountUseCase,
    private val updateAccountUseCase: UpdateAccountUseCase,
    private val ensureYieldCategory: EnsureYieldCategoryUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = account != null

    private val name = MutableStateFlow(account?.name.orEmpty())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(account?.iconKey ?: AppIcon.WALLET.key))

    // The suggestion needs a database read, so it lands after the form is already on
    // screen. This says whether the user got there first — if they did, their choice
    // stands and the suggestion is dropped.
    private var hasPickedIcon = false

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
    private val yieldsInterest = MutableStateFlow(account?.yieldsInterest ?: false)

    private val currency = MutableStateFlow(
        account?.currency ?: baseCurrencyRepository.observe().value
    )

    private val offeredCurrencies = currencyRepository.observeOffered()

    // `combine` takes five, and the form has six pieces of state: the two that answer
    // the same question — which currency, and which ones may be picked — travel
    // together, with the yield flag alongside them.
    private val currencyAndYield = combine(
        currency,
        offeredCurrencies,
        yieldsInterest,
        ::Triple,
    )

    val uiState = combine(
        name,
        selectedIcon,
        isDefault,
        validation,
        currencyAndYield,
    ) { name, selectedIcon, isDefault, validation, (currency, selectableCurrencies, yieldsInterest) ->
        AccountFormUiState(
            name = name,
            selectedIcon = selectedIcon,
            validation = validation,
            isDefault = isDefault,
            yieldsInterest = yieldsInterest,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
            currency = currency,
            canChangeCurrency = !isEditMode,
            selectableCurrencies = selectableCurrencies,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AccountFormUiState(
            name = name.value,
            selectedIcon = selectedIcon.value,
            validation = validation,
            isDefault = isDefault.value,
            yieldsInterest = yieldsInterest.value,
            isEditMode = isEditMode,
            canSubmit = validation[AccountField.NAME] == Validation.Valid,
            canChangeDefault = !(isEditMode && account?.isDefault == true),
            currency = currency.value,
            canChangeCurrency = !isEditMode,
            selectableCurrencies = emptyList(),
        )
    )

    init {
        // Only a new account is suggested one: an existing account opens on the icon
        // it already has, even if another account uses the same.
        if (!isEditMode) {
            viewModelScope.launch {
                val suggestion = suggestAccountIcon()
                if (!hasPickedIcon) selectedIcon.value = suggestion
            }
        }
    }

    fun onAction(action: AccountFormAction) {
        when (action) {
            is AccountFormAction.NameChanged -> {
                changeName(action.name)
            }

            is AccountFormAction.IsDefaultChanged -> {
                isDefault.value = action.isDefault
            }

            is AccountFormAction.YieldsInterestChanged -> {
                yieldsInterest.value = action.yieldsInterest
            }

            is AccountFormAction.IconSelected -> {
                hasPickedIcon = true
                selectedIcon.value = action.icon
            }

            is AccountFormAction.CurrencySelected -> {
                // Guarded by the mode as well as by the form: an edit has no picker to
                // open, and the domain refuses the change anyway.
                if (!isEditMode) currency.value = action.code
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

        // The first account to declare it yields is what brings the category into
        // existence — before the save completes, so no account is ever left declaring
        // a yield with nowhere to classify it.
        if (yieldsInterest.value) {
            runCatching { ensureYieldCategory() }.onFailure {
                crashlytics.recordException(it)
                return@launch
            }
        }

        if (account != null) {
            updateAccountUseCase(
                accountId = account.id,
            ) {
                it.copy(
                    name = name,
                    iconKey = selectedIcon.value.key,
                    isDefault = isDefault.value,
                    yieldsInterest = yieldsInterest.value,
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
            yieldsInterest = yieldsInterest.value,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(CreateAccount(isDefault.value))
            modalManager.dismiss()
        }
    }
}
