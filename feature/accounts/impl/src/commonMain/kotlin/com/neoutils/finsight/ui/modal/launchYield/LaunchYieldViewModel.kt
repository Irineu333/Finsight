@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.launchYield

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.LaunchYield
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.LaunchYieldUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class LaunchYieldViewModel(
    private val account: Account,
    private val launchYieldUseCase: LaunchYieldUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val currentDate
        get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val date = MutableStateFlow(currentDate)
    private val isSubmitting = MutableStateFlow(false)

    val uiState = combine(selectedAccount, date, isSubmitting) { account, date, isSubmitting ->
        if (account == null) {
            LaunchYieldUiState.Loading
        } else {
            LaunchYieldUiState.Content(account = account, date = date, isSubmitting = isSubmitting)
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = LaunchYieldUiState.Loading,
    )

    init {
        viewModelScope.launch {
            // Re-read: the sheet may have been opened over a stale card, and a yield
            // must land on the account as it is now.
            selectedAccount.value = accountRepository.getAccountById(account.id) ?: account
        }
    }

    fun onAction(action: LaunchYieldAction) {
        when (action) {
            is LaunchYieldAction.DateChanged -> date.value = action.date
            is LaunchYieldAction.Submit -> submit(action.amount)
        }
    }

    private fun submit(amount: Double) = viewModelScope.launch {
        val account = selectedAccount.value ?: return@launch
        if (isSubmitting.value) return@launch

        isSubmitting.value = true

        launchYieldUseCase(
            account = account,
            date = date.value,
            amount = amount,
        ).onLeft {
            isSubmitting.value = false
            crashlytics.recordException(it)
            modalManager.showError(it.toUiMessage())
        }.onRight {
            analytics.logEvent(LaunchYield)
            modalManager.dismiss()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is ClosedAccountException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
