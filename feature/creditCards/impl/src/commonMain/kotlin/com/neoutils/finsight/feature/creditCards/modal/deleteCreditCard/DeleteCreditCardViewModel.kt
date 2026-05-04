package com.neoutils.finsight.feature.creditCards.modal.deleteCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.creditCards.event.DeleteCreditCard
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCreditCardViewModel(
    private val creditCard: CreditCard,
    private val deleteCreditCardUseCase: DeleteCreditCardUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteCreditCard() = viewModelScope.launch {
        deleteCreditCardUseCase(creditCard)
            .onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(DeleteCreditCard)
            }
        modalManager.dismissAll()
    }
}
