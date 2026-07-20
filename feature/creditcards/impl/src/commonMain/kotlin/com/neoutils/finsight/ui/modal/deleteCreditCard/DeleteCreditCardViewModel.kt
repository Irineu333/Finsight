package com.neoutils.finsight.ui.modal.deleteCreditCard

import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteCreditCard
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.usecase.DeleteCreditCardUseCase
import com.neoutils.finsight.ui.component.ModalManager
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

    /**
     * A refused action has a reason the user can act on — "this account still has a
     * balance", "this category has transactions". Without this the sheet just did
     * not close and said nothing.
     */
    private fun Throwable.toUiMessage(): UiText = when (this) {
        is AccountException -> error.toUiText()
        else -> UiText.Res(Res.string.retire_action_error_generic)
    }
}
