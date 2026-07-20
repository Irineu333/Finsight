package com.neoutils.finsight.ui.modal.closeCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCreditCard
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.CloseCreditCardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class CloseCreditCardViewModel(
    private val creditCard: CreditCard,
    private val closeCreditCardUseCase: CloseCreditCardUseCase,
    private val entryRepository: IEntryRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /** The card's outstanding balance — see `CloseAccountViewModel`. */
    val balance = MutableStateFlow<Double?>(null)

    init {
        viewModelScope.launch { balance.value = entryRepository.balance(creditCard.accountId) }
    }



    fun closeCreditCard() = viewModelScope.launch {
        closeCreditCardUseCase(creditCard)
            .onLeft {
                crashlytics.recordException(it)
            }.onRight {
                analytics.logEvent(DeleteCreditCard)
            }
        modalManager.dismissAll()
    }
}
