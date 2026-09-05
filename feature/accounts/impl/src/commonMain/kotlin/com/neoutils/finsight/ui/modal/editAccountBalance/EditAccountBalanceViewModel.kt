package com.neoutils.finsight.ui.modal.editAccountBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.AdjustAccountBalance
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonthYear
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * One adjustment, dated. There is no kind of adjustment here and no target period beside
 * the date: [initialDate] is where the entry point chose to open, and everything the form
 * shows follows the date the user ends up on.
 */
@OptIn(ExperimentalTime::class)
class EditAccountBalanceViewModel(
    initialDate: LocalDate,
    private val account: Account,
    private val adjustBalanceUseCase: AdjustBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    private val today = clock.today()

    private val accounts = flow {
        emit(accountRepository.getAllAccounts())
    }

    private val selectedAccount = MutableStateFlow<Account?>(null)

    // The date as the form holds it: text, because that is what the field edits.
    private val date = MutableStateFlow(dayMonthYear.format(initialDate))

    // The date the adjustment happens on: the last text that was a date. Every state of
    // a date being typed parses to nothing, so a form that followed the text would have
    // no date for as long as the user is typing one.
    private val adjustmentDate = MutableStateFlow(initialDate)

    private val currentBalance = combine(selectedAccount, adjustmentDate) { selected, on ->
        if (selected == null) return@combine null
        // One account, one currency — the account's. Nothing is consolidated here, and
        // the balance is read on the very date the adjustment will be written to.
        calculateBalanceUseCase.forAccount(accountId = selected.id, target = on)
    }

    val uiState = combine(
        accounts,
        selectedAccount,
        date,
        currentBalance
    ) { accounts, selectedAccount, date, balance ->
        if (selectedAccount == null || balance == null) {
            EditAccountBalanceUiState.Loading
        } else {
            EditAccountBalanceUiState.Content(
                accounts = accounts,
                selectedAccount = selectedAccount,
                currentBalance = balance,
                date = date,
                today = today,
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

            is EditAccountBalanceAction.ChangeDate -> {
                date.value = action.date
                action.date.toLocalDateOrNull()?.let { adjustmentDate.value = it }
            }

            is EditAccountBalanceAction.Submit -> {
                submit(action.targetBalance)
            }
        }
    }

    private fun submit(targetBalance: Double) = viewModelScope.launch {
        val account = selectedAccount.value ?: return@launch

        adjustBalanceUseCase(
            targetBalance = targetBalance,
            // The date the reference value was read on, which is the one the displayed
            // difference was measured against.
            adjustmentDate = adjustmentDate.value,
            account = account,
        ).onLeft {
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

    private fun String.toLocalDateOrNull(): LocalDate? =
        runCatching { dayMonthYear.parse(this) }.getOrNull()

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is AccountException -> error.toUiText()
        is ClosedAccountException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
