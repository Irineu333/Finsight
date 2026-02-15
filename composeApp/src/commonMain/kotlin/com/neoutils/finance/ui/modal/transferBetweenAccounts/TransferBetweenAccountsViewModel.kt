package com.neoutils.finance.ui.modal.transferBetweenAccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.error.toUiText
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class TransferBetweenAccountsViewModel(
    initialSourceAccount: Account,
    private val transferBetweenAccountsUseCase: TransferBetweenAccountsUseCase,
    accountRepository: com.neoutils.finance.domain.repository.IAccountRepository,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val selectedSourceAccount = MutableStateFlow(initialSourceAccount)
    private val selectedDestinationAccount = MutableStateFlow<Account?>(null)

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedSourceAccount,
        selectedDestinationAccount,
    ) { accounts, source, destination ->
        val currentSource = accounts.firstOrNull { it.id == source.id } ?: accounts.firstOrNull()
        val destinationAccounts = accounts.filter { it.id != currentSource?.id }
        val currentDestination = destination?.takeIf { selected ->
            destinationAccounts.any { it.id == selected.id }
        }

        TransferBetweenAccountsUiState(
            accounts = accounts,
            destinationAccounts = destinationAccounts,
            selectedSourceAccount = currentSource,
            selectedDestinationAccount = currentDestination,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransferBetweenAccountsUiState(
            selectedSourceAccount = initialSourceAccount
        ),
    )

    fun selectSourceAccount(account: Account?) {
        if (account == null) return
        selectedSourceAccount.value = account
        if (selectedDestinationAccount.value?.id == account.id) {
            selectedDestinationAccount.value = null
        }
    }

    fun selectDestinationAccount(account: Account?) {
        selectedDestinationAccount.value = account
    }

    fun transfer(
        amount: Double,
        date: LocalDate,
        title: String?,
    ) = viewModelScope.launch {
        val sourceAccount = uiState.value.selectedSourceAccount ?: return@launch
        val destinationAccount = uiState.value.selectedDestinationAccount ?: return@launch

        transferBetweenAccountsUseCase(
            sourceAccountId = sourceAccount.id,
            destinationAccountId = destinationAccount.id,
            amount = amount,
            date = date,
            title = title,
        ).onLeft {
            _errorMessage.emit(it.error.toUiText().asString())
        }.onRight {
            modalManager.dismiss()
        }
    }
}
