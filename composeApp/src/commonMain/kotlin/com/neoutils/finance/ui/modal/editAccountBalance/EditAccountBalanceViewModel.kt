package com.neoutils.finance.ui.modal.editAccountBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.usecase.AdjustBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustFinalBalanceUseCase
import com.neoutils.finance.domain.usecase.AdjustInitialBalanceUseCase
import com.neoutils.finance.domain.usecase.CalculateBalanceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class EditAccountBalanceUiState(
    val accounts: List<Account> = emptyList(),
    val selectedAccount: Account? = null,
    val currentBalance: Double = 0.0
)

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

    private val selectedAccount = MutableStateFlow(
        runBlocking {
            accountRepository.getAccountById(account.id) ?: account
        }
    )

    private val currentBalance = selectedAccount.map { account ->
        calculateBalanceUseCase(
            target = targetMonth,
            accountId = account.id
        )
    }

    val uiState = combine(
        accounts,
        selectedAccount,
        currentBalance
    ) { accounts, selectedAccount, balance ->
        EditAccountBalanceUiState(
            accounts = accounts,
            selectedAccount = selectedAccount,
            currentBalance = balance
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditAccountBalanceUiState(
            currentBalance = runBlocking {
                calculateBalanceUseCase(
                    target = targetMonth,
                    accountId = account.id
                )
            }
        )
    )

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    fun selectAccount(account: Account) = viewModelScope.launch {
        selectedAccount.value = account
    }

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        val account = checkNotNull(selectedAccount.value)

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
