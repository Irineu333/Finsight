package com.neoutils.finsight.ui.modal.creditCardForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import arrow.core.flatMap
import arrow.core.getOrElse
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.form.CreditCardForm
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.usecase.AddCreditCardUseCase
import com.neoutils.finsight.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finsight.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finsight.extension.CurrencyFormatter
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.CreditCardPeriod
import com.neoutils.finsight.util.DebounceManager
import com.neoutils.finsight.util.ObservableMutableMap
import com.neoutils.finsight.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreditCardFormViewModel(
    private val formatter: CurrencyFormatter,
    private val creditCard: CreditCard?,
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val creditCardPeriod: CreditCardPeriod,
    private val analytics: Analytics,
) : ViewModel() {

    private val isEditMode = creditCard != null

    private val name = MutableStateFlow(creditCard?.name.orEmpty())
    private val selectedIcon = MutableStateFlow(AppIcon.fromKey(creditCard?.iconKey ?: AppIcon.CARD.key))

    private val validation = ObservableMutableMap<CreditCardField, Validation>(
        map = mutableMapOf(
            CreditCardField.NAME to Validation.Valid
        )
    )

    private val limit = MutableStateFlow(
        creditCard?.limit?.let { formatter.format(it) }.orEmpty()
    )

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
                limit = formatter.format(it.limit),
                closingDayUser = it.closingDay.toString(),
                dueDayUser = it.dueDay.toString(),
                iconKey = it.iconKey,
            )
        } ?: CreditCardForm(
            iconKey = AppIcon.CARD.key
        )
    )

    val uiState = combine(form, selectedIcon, validation) { form, selectedIcon, validation ->
        CreditCardFormUiState(
            form = form,
            selectedIcon = selectedIcon,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.isValid(),
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
                limit.value = action.limit
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
                // TODO: register exception
            }.onRight {
                analytics.logEvent("edit_credit_card")
                modalManager.dismissAll()
            }

            return@launch
        }

        addCreditCardUseCase(
            form = form.value,
        ).onLeft {
            // TODO: register exception
        }.onRight {
            analytics.logEvent("create_credit_card")
            modalManager.dismiss()
        }
    }
}
