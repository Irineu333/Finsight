package com.neoutils.finance.ui.modal.deleteCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCreditCardViewModel(
    private val creditCard: CreditCard,
    private val deleteCreditCardUseCase: DeleteCreditCardUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    fun deleteCreditCard() = viewModelScope.launch {
        deleteCreditCardUseCase(creditCard)
        modalManager.dismissAll()
    }
}
