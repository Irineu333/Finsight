@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.TransferBetweenAccounts
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.usecase.SuggestConvertedAmountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransferBetweenAccountsViewModel(
    initialSourceAccount: Account,
    private val transferBetweenAccountsUseCase: TransferBetweenAccountsUseCase,
    private val suggestConvertedAmount: SuggestConvertedAmountUseCase,
    accountRepository: com.neoutils.finsight.domain.repository.IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedSourceAccount = MutableStateFlow(initialSourceAccount)
    private val selectedDestinationAccount = MutableStateFlow<Account?>(null)
    private val sourceAmount = MutableStateFlow(0.0)
    private val date = MutableStateFlow(
        Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    )

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedSourceAccount,
        selectedDestinationAccount,
        sourceAmount,
        date,
    ) { accounts, source, destination, amount, date ->
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
            // Recomputed whenever any of its inputs moves — including the date, because a
            // rate is a fact about a day and changing the day changes which one governs.
            suggestion = if (currentSource != null && currentDestination != null) {
                suggestConvertedAmount(
                    fromCurrency = currentSource.currency,
                    toCurrency = currentDestination.currency,
                    amount = amount,
                    date = date,
                )
            } else {
                null
            },
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
            is TransferBetweenAccountsAction.SourceAmountChanged -> sourceAmount.value = action.amount
            is TransferBetweenAccountsAction.DateChanged -> date.value = action.date
            is TransferBetweenAccountsAction.Submit -> submit(
                sourceAmount = action.sourceAmount,
                destinationAmount = action.destinationAmount,
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
        sourceAmount: Double,
        destinationAmount: Double,
        date: LocalDate,
    ) = viewModelScope.launch {
        val sourceAccount = uiState.value.selectedSourceAccount ?: return@launch
        val destinationAccount = uiState.value.selectedDestinationAccount ?: return@launch

        transferBetweenAccountsUseCase(
            sourceAccountId = sourceAccount.id,
            destinationAccountId = destinationAccount.id,
            sourceAmount = sourceAmount,
            destinationAmount = destinationAmount,
            date = date,
        ).onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.error.toUiText())
        }.onRight {
            analytics.logEvent(TransferBetweenAccounts)
            modalManager.dismiss()
        }
    }
}
