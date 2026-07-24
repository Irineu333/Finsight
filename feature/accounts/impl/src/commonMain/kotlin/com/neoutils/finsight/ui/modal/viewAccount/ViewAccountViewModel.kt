package com.neoutils.finsight.ui.modal.viewAccount

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.exception.DetailNotFoundException
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.UnarchiveAccountUseCase
import com.neoutils.finsight.extension.interceptAbsence
import com.neoutils.finsight.ui.model.toArchivedUi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ViewAccountViewModel(
    private val accountId: Long,
    private val accountRepository: IAccountRepository,
    private val unarchiveAccount: UnarchiveAccountUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val _events = Channel<ViewAccountEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    val uiState = accountRepository.observeAccountById(accountId)
        .interceptAbsence(
            onMissing = { crashlytics.recordException(DetailNotFoundException("Account", accountId)) },
            // Reopening does not remove the row (only the flag flips), so the dismiss on
            // success comes from the use case below, not here. This still fires if the
            // account is deleted through another path while the detail is open.
            onDisappeared = { _events.send(ViewAccountEvent.Dismiss) },
        )
        .map { account ->
            account ?: return@map ViewAccountUiState.Error
            ViewAccountUiState.Content(account = account.toArchivedUi())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ViewAccountUiState.Loading,
        )

    fun onAction(action: ViewAccountAction) {
        when (action) {
            ViewAccountAction.Unarchive -> unarchive()
        }
    }

    // Reversible and innocuous: no confirmation. This detail is archived-only and
    // reached solely from the archived list, so once the account is back in
    // circulation there is nothing left to show — dismiss it. The use case takes the
    // domain Account, resolved by id at the moment of the action.
    private fun unarchive() {
        viewModelScope.launch {
            val account = accountRepository.getAccountById(accountId) ?: return@launch
            unarchiveAccount(account)
                .onRight { _events.send(ViewAccountEvent.Dismiss) }
                .onLeft { crashlytics.recordException(it) }
        }
    }
}
