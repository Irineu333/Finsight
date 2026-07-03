package com.neoutils.finsight.ui.modal.payInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.PayInvoice
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.domain.usecase.PayInvoiceUseCase
import com.neoutils.finsight.ui.component.ModalManager
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
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
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

    fun onAction(action: PayInvoiceAction) {
        when (action) {
            is PayInvoiceAction.SelectAccount -> {
                selectedAccount.value = action.account
            }

            is PayInvoiceAction.Submit -> {
                submit(
                    date = action.date,
                    account = action.account,
                )
            }
        }
    }

    private fun submit(
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
        }.onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(PayInvoice)
            modalManager.dismissAll()
        }
    }
}
