package com.neoutils.finsight.feature.recurring.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.utils.extension.combine
import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.event.ConfirmRecurring
import com.neoutils.finsight.feature.recurring.event.SkipRecurring
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import com.neoutils.finsight.feature.recurring.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.feature.recurring.usecase.SkipRecurringUseCase
import com.neoutils.finsight.feature.transactions.model.Transaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class ConfirmRecurringViewModel(
    private val recurringId: Long,
    private val targetDate: LocalDate,
    private val recurringRepository: IRecurringRepository,
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

    private val recurring = MutableStateFlow<Recurring?>(null)
    private val confirmDate = MutableStateFlow(targetDate.takeIf { it <= currentDate } ?: currentDate)
    private val selectedTarget = MutableStateFlow<Transaction.Target?>(null)
    private val selectedAccount = MutableStateFlow<Account?>(null)
    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    private val accounts = accountRepository.observeAllAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val creditCards = creditCardRepository.observeAllCreditCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        val resolved = recurringRepository.getRecurringById(recurringId)

        if (resolved == null) {
            crashlytics.recordException(RecurringException(RecurringError.NOT_FOUND))
            modalManager.dismiss()
            return@launch
        }

        selectedTarget.value = if (resolved.creditCardId != null) {
            Transaction.Target.CREDIT_CARD
        } else {
            Transaction.Target.ACCOUNT
        }
        selectedAccount.value = resolved.accountId?.let { accountRepository.getAccountById(it) }
        selectedCreditCard.value = resolved.creditCardId?.let { creditCardRepository.getCreditCardById(it) }
        recurring.value = resolved

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

    val uiState = combine(
        recurring.filterNotNull(),
        confirmDate,
        selectedTarget.filterNotNull(),
        selectedAccount,
        selectedCreditCard,
        selectedInvoice,
        invoices,
        accounts,
        creditCards,
    ) { recurring, date, target, account, creditCard, invoice, invoiceList, accounts, creditCards ->

        val defaultAccount = account ?: accounts.firstOrNull { it.isDefault } ?: accounts.firstOrNull()

        ConfirmRecurringUiState.Content(
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
        initialValue = ConfirmRecurringUiState.Loading,
    )

    fun onAction(action: ConfirmRecurringAction) {
        when (action) {
            is ConfirmRecurringAction.TargetSelected -> {
                selectedTarget.value = action.target
                if (action.target.isCreditCard && selectedCreditCard.value == null) {
                    selectedCreditCard.value = creditCards.value.firstOrNull()
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
        val recurring = recurring.value ?: return@launch
        val target = selectedTarget.value ?: return@launch
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
            target = target,
            account = if (target.isAccount) selectedAccount.value else null,
            creditCard = if (target.isCreditCard) selectedCreditCard.value else null,
            invoice = if (target.isCreditCard) selectedInvoice.value else null,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(ConfirmRecurring(recurring, target))
            modalManager.dismiss()
        }
    }

    private fun skip() = viewModelScope.launch {
        val recurring = recurring.value ?: return@launch
        val target = selectedTarget.value ?: return@launch
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate

        skipRecurringUseCase(
            recurring = recurring,
            date = date,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(SkipRecurring(recurring, target))
            modalManager.dismiss()
        }
    }
}
