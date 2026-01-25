package com.neoutils.finance.ui.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finance.ui.component.ModalManager
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
    private val modalManager: ModalManager
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

    fun selectAccount(account: Account?) {
        selectedAccount.value = account
    }

    fun advancePayment(
        amount: Double,
        date: LocalDate,
        account: Account? = selectedAccount.value,
    ) = viewModelScope.launch {
        advanceInvoicePaymentUseCase(
            invoiceId = invoiceId,
            amount = amount,
            date = date,
            account = account ?: checkNotNull(accountRepository.getDefaultAccount()),
        ).onSuccess {
            modalManager.dismissAll()
        }
    }
}

