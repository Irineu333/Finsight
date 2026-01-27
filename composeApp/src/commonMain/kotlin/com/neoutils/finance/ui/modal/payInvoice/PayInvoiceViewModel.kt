package com.neoutils.finance.ui.modal.payInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Account
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finance.domain.usecase.PayInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class PayInvoiceViewModel(
    private val invoiceId: Long,
    private val payInvoicePaymentUseCase: PayInvoicePaymentUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedAccount,
    ) { accounts, account ->
        PayInvoiceUiState(
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = PayInvoiceUiState(),
    )

    fun selectAccount(account: Account?) {
        selectedAccount.value = account
    }

    fun payInvoice(
        date: LocalDate,
        account: Account? = selectedAccount.value,
    ) = viewModelScope.launch {
        val invoiceAmount = calculateInvoiceUseCase(invoiceId)

        if (invoiceAmount == 0.0) {
            payInvoiceUseCase(
                invoiceId = invoiceId,
                paidAt = date,
            )
        } else {
            payInvoicePaymentUseCase(
                invoiceId = invoiceId,
                date = date,
                account = account ?: checkNotNull(accountRepository.getDefaultAccount()),
            )
        }.onRight {
            modalManager.dismissAll()
        }
    }
}
