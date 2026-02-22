package com.neoutils.finsight.ui.modal.deleteCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCreditCardViewModel(
    private val creditCard: CreditCard,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    fun deleteCreditCard() = viewModelScope.launch {
        creditCardRepository.delete(creditCard)
        modalManager.dismissAll()
    }
}
