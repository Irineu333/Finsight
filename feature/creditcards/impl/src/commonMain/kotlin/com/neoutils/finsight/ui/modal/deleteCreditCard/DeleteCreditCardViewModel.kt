package com.neoutils.finsight.ui.modal.deleteCreditCard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCreditCard
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteCreditCardViewModel(
    private val creditCard: CreditCard,
    private val deleteCreditCardUseCase: DeleteCreditCardUseCase,
    private val closeAccountUseCase: CloseAccountUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /** What the confirm button is about to do — asked of the rule, not re-derived. */
    val outcome = MutableStateFlow<CloseAccountUseCase.Outcome?>(null)

    init {
        viewModelScope.launch {
            outcome.value = accountRepository.getAccountById(creditCard.accountId)
                ?.let { closeAccountUseCase.outcomeFor(it) }
                ?: CloseAccountUseCase.Outcome.DELETED
        }
    }


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
