package com.neoutils.finsight.ui.modal.deleteAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val account: Account,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val closeAccountUseCase: CloseAccountUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /**
     * What the confirm button is actually about to do. Asked of the rule's owner
     * rather than re-derived here: the screen names the action, it does not decide
     * it. Null until known, so the modal does not flash the wrong label.
     */
    val outcome = MutableStateFlow<CloseAccountUseCase.Outcome?>(null)

    init {
        viewModelScope.launch { outcome.value = closeAccountUseCase.outcomeFor(account) }
    }


    fun deleteAccount() = viewModelScope.launch {
        deleteAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
