package com.neoutils.finsight.ui.modal.deleteCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCreditCard
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCreditCardViewModel(
    private val creditCard: CreditCard,
    private val deleteCreditCardUseCase: DeleteCreditCardUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteCreditCard() = viewModelScope.launch {
        deleteCreditCardUseCase(creditCard)
        analytics.logEvent(DeleteCreditCard)
        modalManager.dismissAll()
    }
}
