package com.neoutils.finsight.ui.modal.closeAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.CloseAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class CloseAccountViewModel(
    private val account: Account,
    private val closeAccountUseCase: CloseAccountUseCase,
    private val entryRepository: IEntryRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    /**
     * The account's balance — a fact from the ledger, not a prediction of what the
     * use case would do. The modal turns it into copy; the use case refuses a
     * non-zero balance regardless of what the screen shows.
     */
    val balance = MutableStateFlow<Double?>(null)

    init {
        viewModelScope.launch { balance.value = entryRepository.balance(account.id) }
    }



    fun closeAccount() = viewModelScope.launch {
        closeAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
        }
    }
}
