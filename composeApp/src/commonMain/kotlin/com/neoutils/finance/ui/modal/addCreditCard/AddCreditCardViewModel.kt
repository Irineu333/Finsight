@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.modal.addCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.form.AddCreditForm
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.*
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

    private val forms = combine(
        name,
        limit,
        closingDay,
        dueDay,
    ) { name, limit, closingDay, dueDay ->

        val closingDayInt = closingDay.toIntOrNull()
        val dueDayInt = dueDay.toIntOrNull()

        AddCreditForm(
            name = name,
            limit = limit,
            closingDay = closingDay,
            dueDay = dueDay,
            closingDayCalc = dueDayInt?.let { calculateClosingDay(it) },
            dueDayCalc = closingDayInt?.let { calculateDueDay(it) },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddCreditForm()
    )

    val uiState = forms.map { forms ->
        AddCreditCardUiState(
            forms = forms,
            canSubmit = forms.isValid(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AddCreditCardUiState()
    )

    fun onAction(action: AddCreditCardAction) {
        when (action) {
            is AddCreditCardAction.NameChanged -> {
                changeName(action.name)
            }

            is AddCreditCardAction.LimitChanged -> {
                limit.value = action.limit
            }

            is AddCreditCardAction.ClosingDayChanged -> {
                closingDay.value = action.closingDay
            }

            is AddCreditCardAction.DueDayChanged -> {
                dueDay.value = action.dueDay
            }

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
        val forms = forms.value

        if (!forms.isValid()) return@launch

        if (validateCreditCardName(forms.name.text) != null) return@launch

        val effectiveClosingDay = forms.effectiveClosingDay ?: return@launch

        val effectiveDueDay = forms.effectiveDueDay ?: return@launch

        val form = CreditCardForm(
            name = forms.name.text.trim(),
            limit = parseMoneyToDouble(limit.value),
            closingDay = effectiveClosingDay,
            dueDay = effectiveDueDay,
        )

        addCreditCardUseCase(form).onSuccess {
            modalManager.dismiss()
        }
    }

    private fun calculateDueDay(closingDay: Int): Int {
        return ((closingDay - 1 + DEFAULT_DAYS_DIFFERENCE) % 31) + 1
    }

    private fun calculateClosingDay(dueDay: Int): Int {
        return ((dueDay - 1 - DEFAULT_DAYS_DIFFERENCE + 31) % 31) + 1
    }

    private fun parseMoneyToDouble(formatted: String): Double {
        val digitsOnly = formatted
            .replace("R$", "")
            .replace(".", "")
            .replace(",", ".")
            .replace("-", "")
            .trim()

        return digitsOnly.toDoubleOrNull() ?: 0.0
    }

    companion object {
        private const val DEFAULT_DAYS_DIFFERENCE = 8
    }
}
