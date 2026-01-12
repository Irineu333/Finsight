@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class AddCreditCardViewModel(
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val name = MutableStateFlow(FieldForm())
    private val limit = MutableStateFlow("")
    private val closingDay = MutableStateFlow("")
    private val dueDay = MutableStateFlow("")

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
            closingDayCalc = dueDayInt?.let { CreditCardForm.calculateClosingDay(it) },
            dueDayCalc = closingDayInt?.let { CreditCardForm.calculateDueDay(it) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = CreditCardForm()
    )

    val uiState = form.map { form ->
        AddCreditCardUiState(
            form = form,
            canSubmit = form.isValid(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddCreditCardUiState()
    )

    fun onAction(action: AddCreditCardAction) {
        when (action) {
            is AddCreditCardAction.NameChanged -> changeName(action.name)
            is AddCreditCardAction.LimitChanged -> limit.value = action.limit
            is AddCreditCardAction.ClosingDayChanged -> closingDay.value = action.closingDay
            is AddCreditCardAction.DueDayChanged -> dueDay.value = action.dueDay
            is AddCreditCardAction.Submit -> submit()
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
            key = "validate_credit_card_name",
        ) {
            name.update {
                it.copy(
                    validation = validateCreditCardName(newName)?.let { error ->
                        Validation.Error(error)
                    } ?: Validation.Valid,
                )
            }
        }
    }

    private fun submit() = viewModelScope.launch {
        addCreditCardUseCase(form.value)
            .onSuccess {
                modalManager.dismiss()
            }
    }
}
