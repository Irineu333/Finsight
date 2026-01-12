package com.neoutils.finance.ui.modal.editCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finance.domain.usecase.ValidateCreditCardNameUseCase
import com.neoutils.finance.extension.toMoneyFormat
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.util.DebounceManager
import com.neoutils.finance.util.FieldForm
import com.neoutils.finance.util.Validation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class EditCreditCardViewModel(
    private val creditCard: CreditCard,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val validateCreditCardName: ValidateCreditCardNameUseCase,
    private val modalManager: ModalManager,
    private val debounceManager: DebounceManager,
) : ViewModel() {

    private val name = MutableStateFlow(
        FieldForm(
            text = creditCard.name,
            validation = Validation.Valid
        )
    )

    private val limit = MutableStateFlow(creditCard.limit.toMoneyFormat())
    private val closingDay = MutableStateFlow(creditCard.closingDay.toString())
    private val dueDay = MutableStateFlow(creditCard.dueDay.toString())

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
        initialValue = CreditCardForm(
            name = FieldForm(
                text = creditCard.name,
                validation = Validation.Valid
            ),
            limit = creditCard.limit.toMoneyFormat(),
            closingDayUser = creditCard.closingDay.toString(),
            dueDayUser = creditCard.dueDay.toString(),
        )
    )

    val uiState = form.map { form ->
        EditCreditCardUiState(
            form = form,
            canSubmit = form.isValid(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = EditCreditCardUiState(
            form = form.value,
            canSubmit = form.value.isValid(),
        )
    )

    fun onAction(action: EditCreditCardAction) {
        when (action) {
            is EditCreditCardAction.NameChanged -> changeName(action.name)

            is EditCreditCardAction.LimitChanged -> {
                limit.value = action.limit
            }

            is EditCreditCardAction.ClosingDayChanged -> {
                closingDay.value = action.closingDay
            }

            is EditCreditCardAction.DueDayChanged -> {
                dueDay.value = action.dueDay
            }

            is EditCreditCardAction.Submit -> submit()
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
                    validation = validateCreditCardName(
                        name = newName,
                        ignoreId = creditCard.id
                    )?.let { error ->
                        Validation.Error(error)
                    } ?: Validation.Valid,
                )
            }
        }
    }

    private fun submit() = viewModelScope.launch {
        form.value.build(id = creditCard.id).onSuccess { builtCreditCard ->
            updateCreditCardUseCase(creditCard.id) {
                it.copy(
                    name = builtCreditCard.name,
                    limit = builtCreditCard.limit,
                    closingDay = builtCreditCard.closingDay,
                    dueDay = builtCreditCard.dueDay,
                )
            }.onSuccess {
                modalManager.dismissAll()
            }
        }
    }
}
