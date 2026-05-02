package com.neoutils.finsight.feature.accounts.modal.deleteAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.feature.accounts.event.DeleteAccount
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.feature.accounts.usecase.DeleteAccountUseCase
import com.neoutils.finsight.core.ui.component.ModalManager
import kotlinx.coroutines.launch

class DeleteAccountViewModel(
    private val account: Account,
    private val deleteAccountUseCase: DeleteAccountUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    fun deleteAccount() = viewModelScope.launch {
        deleteAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
