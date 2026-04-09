package com.neoutils.finsight.ui.modal.editAccountBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finsight.domain.usecase.AdjustInitialBalanceUseCase
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
    private val adjustInitialBalanceUseCase: AdjustInitialBalanceUseCase,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager
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
            EditAccountBalanceModal.Type.CURRENT -> {
                adjustBalanceUseCase(
                    targetBalance = targetBalance,
                    adjustmentDate = currentDate,
                    account = account,
                )
            }

            EditAccountBalanceModal.Type.FINAL -> {
                adjustFinalBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth,
                    account = account,
                )
            }

            EditAccountBalanceModal.Type.INITIAL -> {
                adjustInitialBalanceUseCase(
                    targetBalance = targetBalance,
                    targetMonth = targetMonth,
                    account = account,
                )
            }
        }

        modalManager.dismiss()
    }
}
