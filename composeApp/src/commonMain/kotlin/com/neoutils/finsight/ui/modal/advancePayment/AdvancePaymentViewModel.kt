package com.neoutils.finsight.ui.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class AdvancePaymentViewModel(
    private val invoiceId: Long,
    private val advanceInvoicePaymentUseCase: AdvanceInvoicePaymentUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)

    private val accounts = flow {
        emit(accountRepository.getAllAccounts())
    }

    val uiState = combine(
        accounts,
        selectedAccount,
    ) { accounts, account ->
        AdvancePaymentUiState(
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdvancePaymentUiState(),
    )

    fun onAction(action: AdvancePaymentAction) {
        when (action) {
            is AdvancePaymentAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is AdvancePaymentAction.Submit -> {
                submit(
                    amount = action.amount,
                    date = action.date,
                    account = action.account,
                )
            }
        }
    }

    private fun submit(
        amount: Double,
        date: LocalDate,
        account: Account? = selectedAccount.value,
    ) = viewModelScope.launch {
        advanceInvoicePaymentUseCase(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            account = account ?: checkNotNull(accountRepository.getDefaultAccount()),
        ).onRight {
            analytics.logEvent("advance_invoice_payment")
            modalManager.dismissAll()
        }
    }
}
