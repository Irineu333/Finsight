package com.neoutils.finance.ui.modal.editCreditCardName

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.usecase.UpdateCreditCardUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditCreditCardNameViewModel(
    private val creditCardId: Long,
    private val updateCreditCardUseCase: UpdateCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun updateName(newName: String) = viewModelScope.launch {
        updateCreditCardUseCase(creditCardId) {
            it.copy(name = newName)
        }.onSuccess {
            modalManager.dismiss()
        }
    }
}
