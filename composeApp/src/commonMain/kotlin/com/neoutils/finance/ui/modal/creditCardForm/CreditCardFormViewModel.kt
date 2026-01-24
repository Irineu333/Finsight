package com.neoutils.finance.ui.modal.creditCardForm

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finance.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finance.extension.then
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.ObservableMutableMap
import com.neoutils.finance.util.CreditCardPeriod
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.Validation
import com.neoutils.finance.util.validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class CreditCardFormViewModel(
    private val creditCard: CreditCard?,
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
    private val creditCardPeriod: CreditCardPeriod,
) : ViewModel() {

    private val isEditMode = creditCard != null

    private val name = MutableStateFlow(creditCard?.name.orEmpty())

    private val validation = ObservableMutableMap<CreditCardField, Validation>(
        map = mutableMapOf(
            CreditCardField.NAME to Validation.Valid
        )
    )

    private val limit = MutableStateFlow(
        creditCard?.limit?.toMoneyFormat().orEmpty()
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
    ) { name, limit, closingDay, dueDay ->
        val closingDayInt = closingDay.toIntOrNull()
        val dueDayInt = dueDay.toIntOrNull()

        CreditCardForm(
            name = name,
            limit = limit,
            closingDayUser = closingDay,
            dueDayUser = dueDay,
            closingDayCalc = dueDayInt?.let { creditCardPeriod.calculateClosingDay(it) },
            dueDayCalc = closingDayInt?.let { creditCardPeriod.calculateDueDay(it) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = creditCard?.let {
            CreditCardForm(
                name = it.name,
                limit = it.limit.toMoneyFormat(),
                closingDayUser = it.closingDay.toString(),
                dueDayUser = it.dueDay.toString(),
            )
        } ?: CreditCardForm()
    )

    val uiState = combine(form, validation) { form, validation ->
        CreditCardFormUiState(
            form = form,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.isValid(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardFormUiState(
            form = form.value,
            validation = validation,
            isEditMode = isEditMode,
            canSubmit = form.value.isValid(),
        )
    )

    fun onAction(action: CreditCardFormAction) {
        when (action) {
            is CreditCardFormAction.NameChanged -> changeName(action.name)
            is CreditCardFormAction.LimitChanged -> {
                limit.value = action.limit
            }

            is CreditCardFormAction.ClosingDayChanged -> {
                closingDay.value = action.closingDay
            }

            is CreditCardFormAction.DueDayChanged -> {
                dueDay.value = action.dueDay
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
            ).validation
        }
    }

    private fun submit() = viewModelScope.launch {

        if (creditCard != null) {
            form.value.build(id = creditCard.id).then { creditCard ->
                updateCreditCardUseCase(creditCard.id) {
                    it.copy(
                        name = creditCard.name,
                        limit = creditCard.limit,
                        closingDay = creditCard.closingDay,
                        dueDay = creditCard.dueDay,
                    )
                }
            }.onSuccess {
                modalManager.dismissAll()
            }

            return@launch
        }

        addCreditCardUseCase(form.value).onSuccess {
            modalManager.dismiss()
        }
    }
}
