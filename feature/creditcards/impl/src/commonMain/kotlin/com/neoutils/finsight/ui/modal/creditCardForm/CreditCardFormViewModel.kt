package com.neoutils.finsight.ui.modal.creditCardForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.CreateCreditCard
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.EditCreditCard
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.format
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.CreditCardPeriod
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ObservableMutableMap
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreditCardFormViewModel(
    // Injected rather than read from the composition local: this one formats the
    // pre-filled limit outside any composition.
    private val formatter: CurrencyFormatter,
    private val creditCard: CreditCard?,
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val creditCardPeriod: CreditCardPeriod,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /**
     * What the limit is typed and read back in (design D17). An existing card has an
     * account, and that account states it. A card being *created* does not — its
     * `LIABILITY` account is only born on insert — so the repository that will
     * denominate it is asked what it is about to write, rather than the question being
     * answered a second time here.
     *
     * Either answer is a suspending read, so the form starts without a currency and the
     * limit field simply does not format until it arrives.
     */
    private val currency = flow {
        emit(
            creditCard
                ?.let { accountRepository.currencyOf(it) }
                ?: creditCardRepository.currencyForNewCard()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    private fun prefilledLimit(currency: String?): String {
        if (creditCard == null || currency == null) return ""

        return formatter.format(
            DisplayAmount.magnitude(creditCard.limit, currency, isApproximate = false)
        )
    }

    private val isEditMode = creditCard != null

    private val name = MutableStateFlow(creditCard?.name.orEmpty())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(creditCard?.iconKey ?: AppIcon.CARD.key))

    private val validation = ObservableMutableMap<CreditCardField, Validation>(
        map = mutableMapOf(
            CreditCardField.NAME to Validation.Valid
        )
    )

    private val typedLimit = MutableStateFlow<String?>(null)

    // The stored limit of an existing card until the user types over it — pre-filling it
    // has to wait for the currency it is read back in.
    private val limit = combine(typedLimit, currency) { typed, currency ->
        typed ?: prefilledLimit(currency)
    }

    private val closingDay = MutableStateFlow(
        creditCard?.closingDay?.toString().orEmpty()
    )

    private val dueDay = MutableStateFlow(
        creditCard?.dueDay?.toString().orEmpty()
    )

    private val form = combine(
        name,
        limit,
        closingDay,
        dueDay,
        selectedIcon,
    ) { name, limit, closingDay, dueDay, selectedIcon ->
        val closingDayInt = closingDay.toIntOrNull()
        val dueDayInt = dueDay.toIntOrNull()

        CreditCardForm(
            name = name,
            limit = limit,
            closingDayUser = closingDay,
            dueDayUser = dueDay,
            closingDayCalc = dueDayInt?.let { creditCardPeriod.calculateClosingDay(it) },
            dueDayCalc = closingDayInt?.let { creditCardPeriod.calculateDueDay(it) },
            iconKey = selectedIcon.key,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = creditCard?.let {
            CreditCardForm(
                name = it.name,
                limit = prefilledLimit(currency.value),
                closingDayUser = it.closingDay.toString(),
                dueDayUser = it.dueDay.toString(),
                iconKey = it.iconKey,
            )
        } ?: CreditCardForm(
            iconKey = AppIcon.CARD.key
        )
    )

    val uiState = combine(
        form,
        selectedIcon,
        validation,
        currency,
    ) { form, selectedIcon, validation, currency ->
        CreditCardFormUiState(
            form = form,
            selectedIcon = selectedIcon,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.isValid(),
            currency = currency,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardFormUiState(
            form = form.value,
            selectedIcon = selectedIcon.value,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.value.isValid(),
        )
    )

    fun onAction(action: CreditCardFormAction) {
        when (action) {
            is CreditCardFormAction.NameChanged -> {
                changeName(action.name)
            }

            is CreditCardFormAction.LimitChanged -> {
                typedLimit.value = action.limit
            }

            is CreditCardFormAction.ClosingDayChanged -> {
                closingDay.value = action.closingDay
            }

            is CreditCardFormAction.DueDayChanged -> {
                dueDay.value = action.dueDay
            }

            is CreditCardFormAction.IconSelected -> {
                selectedIcon.value = action.icon
            }

            is CreditCardFormAction.Submit -> submit()
        }
    }

    private fun changeName(newName: String) {
        name.value = newName
        validation[CreditCardField.NAME] = Validation.Validating

        debounceManager(
            scope = viewModelScope,
            key = "validate_credit_card_name",
        ) {
            validation[CreditCardField.NAME] = validateCreditCardName(
                name = newName,
                ignoreId = creditCard?.id
            ).map {
                Validation.Valid
            }.getOrElse { error ->
                Validation.Error(error.toUiText())
            }
        }
    }

    private fun submit() = viewModelScope.launch {

        if (creditCard != null) {
            form.value.build(
                id = creditCard.id,
            ).flatMap { creditCard ->
                updateCreditCardUseCase(creditCard.id) {
                    it.copy(
                        name = creditCard.name,
                        limit = creditCard.limit,
                        closingDay = creditCard.closingDay,
                        dueDay = creditCard.dueDay,
                        iconKey = creditCard.iconKey,
                    )
                }
            }.onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(EditCreditCard)
                modalManager.dismissAll()
            }

            return@launch
        }

        addCreditCardUseCase(
            form = form.value,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(CreateCreditCard)
            modalManager.dismiss()
        }
    }
}
