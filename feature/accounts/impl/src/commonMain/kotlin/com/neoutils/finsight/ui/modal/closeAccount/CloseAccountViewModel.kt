package com.neoutils.finsight.ui.modal.closeAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class CloseAccountViewModel(
    private val account: Account,
    private val closeAccountUseCase: CloseAccountUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {


    fun closeAccount() = viewModelScope.launch {
        closeAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
