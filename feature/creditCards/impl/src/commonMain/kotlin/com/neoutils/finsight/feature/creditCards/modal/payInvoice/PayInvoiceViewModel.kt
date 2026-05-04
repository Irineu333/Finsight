package com.neoutils.finsight.feature.creditCards.modal.payInvoice

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.creditCards.event.PayInvoice
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.feature.creditCards.usecase.PayInvoicePaymentUseCase
import com.neoutils.finsight.feature.creditCards.usecase.PayInvoiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class PayInvoiceViewModel(
    private val invoiceId: Long,
    private val payInvoicePaymentUseCase: PayInvoicePaymentUseCase,
    private val payInvoiceUseCase: PayInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val accountRepository: IAccountRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val selectedAccount = MutableStateFlow<Account?>(null)

    private val datesFlow = flow {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)
        val creditCard = invoice?.let { creditCardRepository.getCreditCardById(it.creditCardId) }
        emit(
            if (invoice != null && creditCard != null) {
                invoice.closingMonth.safeOnDay(creditCard.closingDay) to
                        invoice.dueMonth.safeOnDay(creditCard.dueDay)
            } else null
        )
    }

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        selectedAccount,
        datesFlow,
    ) { accounts, account, dates ->
        PayInvoiceUiState(
            accounts = accounts,
            selectedAccount = account ?: accounts.firstOrNull { it.isDefault },
            closingDate = dates?.first,
            dueDate = dates?.second,
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
