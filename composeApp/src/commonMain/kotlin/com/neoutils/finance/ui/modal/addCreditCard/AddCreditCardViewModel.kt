package com.neoutils.finance.ui.modal.addCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.domain.model.form.CreditCardForm
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class AddCreditCardViewModel(
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun addCreditCard(
        creditCardForm: CreditCardForm
    ) = viewModelScope.launch {
        addCreditCardUseCase(
            form = creditCardForm
        ).onSuccess {
            modalManager.dismiss()
        }
    }
}
