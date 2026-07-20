package com.neoutils.finsight.ui.modal.archiveAccount

import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.retire_action_error_generic
import com.neoutils.finsight.util.UiText
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.DeleteAccount
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.usecase.ArchiveAccountUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.launch

class ArchiveAccountViewModel(
    private val account: Account,
    private val archiveAccountUseCase: ArchiveAccountUseCase,
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



    fun archiveAccount() = viewModelScope.launch {
        archiveAccountUseCase(account).onRight {
            analytics.logEvent(DeleteAccount)
            modalManager.dismissAll()
        }.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }
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
