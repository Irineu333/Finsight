package com.neoutils.finsight.ui.modal.editAccountBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.AdjustAccountBalance
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustOpeningBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EditAccountBalanceViewModel(
    private val type: EditAccountBalanceModal.Type,
    private val targetMonth: YearMonth,
    private val account: Account,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val adjustFinalBalanceUseCase: AdjustFinalBalanceUseCase,
    private val adjustOpeningBalanceUseCase: AdjustOpeningBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val accounts = flow {
        emit(accountRepository.getAllAccounts())
    }

    private val selectedAccount = MutableStateFlow<Account?>(null)

    private val currentBalance = selectedAccount.map { selected ->
        selected?.let {
            calculateBalanceUseCase(
                target = targetMonth,
                accountId = it.id
            )
        }
    }

    private val timeZone get() = TimeZone.currentSystemDefault()

    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    val uiState = combine(
        accounts,
        selectedAccount,
        currentBalance
    ) { accounts, selectedAccount, balance ->
        if (selectedAccount == null || balance == null) {
            EditAccountBalanceUiState.Loading
        } else {
            EditAccountBalanceUiState.Content(
                accounts = accounts,
                selectedAccount = selectedAccount,
                currentBalance = balance
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditAccountBalanceUiState.Loading
    )

    init {
        initialAccount()
    }

    private fun initialAccount() = viewModelScope.launch {
        selectedAccount.value = accountRepository.getAccountById(account.id) ?: account
    }

    fun onAction(action: EditAccountBalanceAction) {
        when (action) {
            is EditAccountBalanceAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is EditAccountBalanceAction.Submit -> {
                submit(action.targetBalance)
            }
        }
    }

    private fun submit(targetBalance: Double) = viewModelScope.launch {
        val account = selectedAccount.value ?: return@launch

        when (type) {
            EditAccountBalanceModal.Type.CURRENT -> adjustBalanceUseCase(
                targetBalance = targetBalance,
                adjustmentDate = currentDate,
                account = account,
            )

            EditAccountBalanceModal.Type.FINAL -> adjustFinalBalanceUseCase(
                targetBalance = targetBalance,
                targetMonth = targetMonth,
                account = account,
            )

            EditAccountBalanceModal.Type.INITIAL -> adjustOpeningBalanceUseCase(
                targetBalance = targetBalance,
                targetMonth = targetMonth,
                account = account,
            )
        }.onLeft {
            when (it) {
                // No change to make: the target equals the current balance. Nothing
                // failed, so close quietly — not a false success, there was nothing.
                is AccountNotAdjustedException -> modalManager.dismiss()
                // A genuine refusal (e.g. the account was archived mid-flight) must
                // say why and keep the sheet open, not close as if it worked.
                else -> {
                    crashlytics.recordException(it)
                    modalManager.showError(it.toUiMessage())
                }
            }
        }.onRight {
            analytics.logEvent(AdjustAccountBalance)
            modalManager.dismiss()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is ClosedAccountException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
