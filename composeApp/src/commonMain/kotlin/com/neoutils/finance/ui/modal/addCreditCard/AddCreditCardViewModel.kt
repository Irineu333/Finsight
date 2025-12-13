package com.neoutils.finance.ui.modal.addCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.usecase.AddCreditCardUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class AddCreditCardViewModel(
    private val addCreditCardUseCase: AddCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun addCreditCard(name: String, limit: Double) {
        viewModelScope.launch {
            addCreditCardUseCase(name, limit)
            modalManager.dismiss()
        }
    }
}
