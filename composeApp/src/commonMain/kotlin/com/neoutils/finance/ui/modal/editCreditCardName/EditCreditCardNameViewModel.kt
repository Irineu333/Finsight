package com.neoutils.finance.ui.modal.editCreditCardName

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class EditCreditCardNameViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    fun updateName(newName: String) = viewModelScope.launch {
        val creditCard = creditCardRepository.getCreditCardById(creditCardId)
        if (creditCard != null) {
            creditCardRepository.update(creditCard.copy(name = newName))
        }
        modalManager.dismiss()
    }
}
