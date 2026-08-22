@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.transferBetweenAccounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.EditTransferBetweenAccounts
import com.neoutils.finsight.domain.analytics.event.TransferBetweenAccounts
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.SuggestCrossCurrencyAmountUseCase
import com.neoutils.finsight.domain.usecase.TransferBetweenAccountsUseCase
import com.neoutils.finsight.domain.usecase.UpdateTransferUseCase
import com.neoutils.finsight.extension.destinationLeg
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlin.math.abs
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransferBetweenAccountsViewModel(
    initialSourceAccount: Account,
    /**
     * The operation being corrected, and `null` while one is being registered. It is
     * the only thing that tells the two modes apart — the form itself is the same.
     */
    private val transaction: Transaction?,
    private val transferBetweenAccountsUseCase: TransferBetweenAccountsUseCase,
    private val updateTransferUseCase: UpdateTransferUseCase,
    private val suggestCrossCurrencyAmount: SuggestCrossCurrencyAmountUseCase,
    accountRepository: IAccountRepository,
    /**
     * The app's own clock, and not the system's: the form bounds its date picker by it
     * and the rule that refuses a future date is stated against it, so a third reading
     * of "today" here is one the two of them could disagree with.
     */
    clock: Clock,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val isEditMode = transaction != null

    // The two ends of an operation being corrected are the operation's own, read from
    // the ledger's owners of that reading rather than picked out of the entries here.
    // The source is already resolved by the modal, which cannot be built without it.
    private val initialDestinationAccount = transaction?.entries?.destinationLeg()?.account

    private val selectedSourceAccount = MutableStateFlow(initialSourceAccount)
    private val selectedDestinationAccount = MutableStateFlow(initialDestinationAccount)
    private val amount = MutableStateFlow(transaction?.amount ?: 0.0)
    private val date = MutableStateFlow(transaction?.date ?: clock.today())

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedSourceAccount,
        selectedDestinationAccount,
        amount,
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
            // Read as of the operation's own date, so a transfer being registered for
            // last month is offered last month's rate and not today's.
            suggestion = if (currentSource != null && currentDestination != null) {
                suggestCrossCurrencyAmount(
                    amount = amount,
                    from = currentSource.currency,
                    to = currentDestination.currency,
                    on = date,
                )
            } else {
                null
            },
            isEditMode = isEditMode,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        // Both ends, not only the source: a correction opens already denominated, and
        // the field that shows what arrives has to know its currency from the first
        // composition rather than after the accounts flow lands.
        initialValue = TransferBetweenAccountsUiState(
            selectedSourceAccount = initialSourceAccount,
            selectedDestinationAccount = initialDestinationAccount,
            isEditMode = isEditMode,
        ),
    )

    fun onAction(action: TransferBetweenAccountsAction) {
        when (action) {
            is TransferBetweenAccountsAction.SelectSourceAccount -> selectSourceAccount(action.account)
            is TransferBetweenAccountsAction.SelectDestinationAccount -> selectDestinationAccount(action.account)
            is TransferBetweenAccountsAction.ChangeAmount -> amount.value = action.amount
            is TransferBetweenAccountsAction.ChangeDate -> date.value = action.date
            is TransferBetweenAccountsAction.Submit -> submit(
                amount = action.amount,
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
        amount: Double,
        destinationAmount: Double,
        date: LocalDate,
    ) = viewModelScope.launch {
        val sourceAccount = uiState.value.selectedSourceAccount ?: return@launch
        val destinationAccount = uiState.value.selectedDestinationAccount ?: return@launch

        // Two numbers only where two numbers mean something. A same-currency transfer
        // moves one figure, and stating it twice would be the form inventing a rate of 1.
        val arriving = destinationAmount.takeIf { uiState.value.isCrossCurrency }

        val result = if (transaction != null) {
            updateTransferUseCase(
                transactionId = transaction.id,
                sourceAccountId = sourceAccount.id,
                destinationAccountId = destinationAccount.id,
                amount = amount,
                date = date,
                destinationAmount = arriving,
            )
        } else {
            transferBetweenAccountsUseCase(
                sourceAccountId = sourceAccount.id,
                destinationAccountId = destinationAccount.id,
                amount = amount,
                date = date,
                destinationAmount = arriving,
            )
        }

        result.onLeft {
            crashlytics.recordException(it)
            modalManager.showError(it.error.toUiText())
        }.onRight {
            analytics.logEvent(
                if (isEditMode) EditTransferBetweenAccounts else TransferBetweenAccounts
            )
            modalManager.dismiss()
        }
    }
}
