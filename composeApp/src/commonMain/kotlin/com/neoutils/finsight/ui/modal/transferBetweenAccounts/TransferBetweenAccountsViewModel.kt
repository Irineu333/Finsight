package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class TransferBetweenAccountsViewModel(
    initialSourceAccount: Account,
    private val transferBetweenAccountsUseCase: TransferBetweenAccountsUseCase,
    accountRepository: com.neoutils.finsight.domain.repository.IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val selectedSourceAccount = MutableStateFlow(initialSourceAccount)
    private val selectedDestinationAccount = MutableStateFlow<Account?>(null)

    private val _events = Channel<TransferBetweenAccountsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

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

    fun onAction(action: TransferBetweenAccountsAction) {
        when (action) {
            is TransferBetweenAccountsAction.SelectSourceAccount -> selectSourceAccount(action.account)
            is TransferBetweenAccountsAction.SelectDestinationAccount -> selectDestinationAccount(action.account)
            is TransferBetweenAccountsAction.Submit -> submit(
                amount = action.amount,
                date = action.date,
            )
        }
    }

    private fun selectSourceAccount(account: Account?) {
        if (account == null) return
        selectedSourceAccount.value = account
        if (selectedDestinationAccount.value?.id == account.id) {
            selectedDestinationAccount.value = null
        }
    }

    private fun selectDestinationAccount(account: Account?) {
        selectedDestinationAccount.value = account
    }

    private fun submit(
        amount: Double,
        date: LocalDate,
    ) = viewModelScope.launch {
        val sourceAccount = uiState.value.selectedSourceAccount ?: return@launch
        val destinationAccount = uiState.value.selectedDestinationAccount ?: return@launch

        transferBetweenAccountsUseCase(
            sourceAccountId = sourceAccount.id,
            destinationAccountId = destinationAccount.id,
            amount = amount,
            date = date,
        ).onLeft {
            _events.send(
                TransferBetweenAccountsEvent.ShowError(
                    it.error.toUiText()
                )
            )
        }.onRight {
            analytics.logEvent("transfer_between_accounts")
            modalManager.dismiss()
        }
    }
}
