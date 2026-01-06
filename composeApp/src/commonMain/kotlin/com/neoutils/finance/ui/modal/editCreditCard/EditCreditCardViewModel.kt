package com.neoutils.finance.ui.modal.editCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class EditCreditCardViewModel(
    private val creditCardId: Long,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun update(form: CreditCardForm) = viewModelScope.launch {
        val closingDay = form.closingDay ?: return@launch
        val dueDay = form.dueDay ?: return@launch

        updateCreditCardUseCase(creditCardId) {
            it.copy(
                name = form.name.trim(),
                limit = form.limit,
                closingDay = closingDay,
                dueDay = dueDay
            )
        }.onSuccess {
            modalManager.dismissAll()
        }
    }
}
