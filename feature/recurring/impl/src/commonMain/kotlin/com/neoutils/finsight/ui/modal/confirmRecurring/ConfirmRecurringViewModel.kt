package com.neoutils.finsight.ui.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.ConfirmRecurring
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.analytics.event.SkipRecurring
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ConfirmRecurringViewModel(
    val recurring: Recurring,
    private val targetDate: LocalDate,
    private val accountRepository: IAccountRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val initialTarget = if (recurring.creditCard != null) {
        Transaction.Target.CREDIT_CARD
    } else {
        Transaction.Target.ACCOUNT
    }
    private val confirmDate = MutableStateFlow(targetDate.takeIf { it <= currentDate } ?: currentDate)
    private val selectedTarget = MutableStateFlow(initialTarget)
    private val selectedAccount = MutableStateFlow(recurring.account)
    private val selectedCreditCard = MutableStateFlow(recurring.creditCard)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    init {
        viewModelScope.launch {
            selectedCreditCard.collectLatest { creditCard ->
                if (creditCard == null) {
                    invoices.value = emptyList()
                    selectedInvoice.value = null
                    return@collectLatest
                }

                val allInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)
                invoices.value = allInvoices
                selectedInvoice.value = allInvoices.firstOrNull { it.status.isOpen } ?: allInvoices.firstOrNull()
            }
        }
    }

    val uiState = combine(
        confirmDate,
        selectedTarget,
        selectedAccount,
        selectedCreditCard,
        selectedInvoice,
        invoices,
        accountRepository.observeAllAccounts(),
        creditCardRepository.observeAllCreditCards(),
    ) { date, target, account, creditCard, invoice, invoiceList, accounts, creditCards ->
        val defaultAccount = account ?: accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull()

        ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = date,
            selectedTarget = target,
            accounts = accounts,
            selectedAccount = defaultAccount,
            creditCards = creditCards,
            selectedCreditCard = creditCard,
            invoices = invoiceList,
            selectedInvoice = invoice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = targetDate.takeIf { it <= currentDate } ?: currentDate,
            selectedTarget = initialTarget,
            selectedAccount = recurring.account,
            selectedCreditCard = recurring.creditCard,
        ),
    )

    fun onAction(action: ConfirmRecurringAction) {
        when (action) {
            is ConfirmRecurringAction.TargetSelected -> {
                selectedTarget.value = action.target
                if (action.target.isCreditCard && selectedCreditCard.value == null) {
                    selectedCreditCard.value = uiState.value.creditCards.firstOrNull()
                }
            }

            is ConfirmRecurringAction.AccountSelected -> selectedAccount.value = action.account
            is ConfirmRecurringAction.CreditCardSelected -> selectedCreditCard.value = action.creditCard
            is ConfirmRecurringAction.DateChanged -> {
                confirmDate.value = action.date.takeIf { it <= currentDate } ?: currentDate
            }

            is ConfirmRecurringAction.InvoiceSelected -> selectedInvoice.value = action.invoice
            is ConfirmRecurringAction.Confirm -> confirm(action.amount)
            is ConfirmRecurringAction.Skip -> skip()
        }
    }

    private fun confirm(amount: String) = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate

        val parsedAmount = amount.filter { it.isDigit() }
            .toLongOrNull()
            ?.toDouble()
            ?.div(100)
            ?: recurring.amount

        confirmRecurringUseCase(
            recurring = recurring,
            date = date,
            amount = parsedAmount,
            target = uiState.value.selectedTarget,
            account = if (uiState.value.selectedTarget.isAccount) uiState.value.selectedAccount else null,
            creditCard = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedCreditCard else null,
            invoice = if (uiState.value.selectedTarget.isCreditCard) uiState.value.selectedInvoice else null,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(ConfirmRecurring(recurring, uiState.value.selectedTarget))
            modalManager.dismiss()
        }
    }

    private fun skip() = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate
        skipRecurringUseCase(
            recurring = recurring,
            date = date,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(SkipRecurring(recurring, uiState.value.selectedTarget))
            modalManager.dismiss()
        }
    }
}
