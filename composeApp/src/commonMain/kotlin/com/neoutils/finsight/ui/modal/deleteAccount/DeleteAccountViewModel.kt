package com.neoutils.finsight.ui.modal.deleteAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteAccount
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.DeleteAccountUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val account: Account,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    fun deleteAccount() = viewModelScope.launch {
        deleteAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            // TODO: Show error message to user
        }
    }
}
