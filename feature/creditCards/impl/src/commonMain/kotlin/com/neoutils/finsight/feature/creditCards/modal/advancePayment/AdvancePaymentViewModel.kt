@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.modal.advancePayment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.analytics.Analytics
import com.neoutils.finsight.core.analytics.crashlytics.Crashlytics
import com.neoutils.finsight.core.ui.component.ModalManager
import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.creditCards.error.InvoiceError
import com.neoutils.finsight.feature.creditCards.event.AdvanceInvoicePayment
import com.neoutils.finsight.feature.creditCards.exception.InvoiceException
import com.neoutils.finsight.feature.creditCards.model.form.AdvancePaymentForm
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.creditCards.usecase.AdvanceInvoicePaymentUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AdvancePaymentViewModel(
    private val invoiceId: Long,
    private val advanceInvoicePaymentUseCase: AdvanceInvoicePaymentUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val accountRepository: IAccountRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

    private val form = MutableStateFlow<AdvancePaymentForm?>(null)

    init {
        setup()
    }

    private fun setup() = viewModelScope.launch {
        val invoice = invoiceRepository.getInvoiceById(invoiceId)

        if (invoice == null) {
            crashlytics.recordException(InvoiceException(InvoiceError.NotFound))

            form.value = AdvancePaymentForm(
                invoice = null,
                creditCard = null,
                account = accountRepository.getDefaultAccount(),
                date = currentDate,
                currentBillAmount = 0.0,
                today = currentDate,
            )

            return@launch
        }

        val creditCard = creditCardRepository.getCreditCardById(invoice.creditCardId)

        if (creditCard == null) {
            crashlytics.recordException(InvoiceException(InvoiceError.CreditCardNotFound))

            form.value = AdvancePaymentForm(
                invoice = invoice,
                creditCard = null,
                account = accountRepository.getDefaultAccount(),
                date = currentDate,
                currentBillAmount = calculateInvoiceUseCase(invoiceId),
                today = currentDate,
            )

            return@launch
        }

        val openingDate = invoice.openingMonth.safeOnDay(creditCard.closingDay)
        val closingDate = invoice.closingMonth.safeOnDay(creditCard.closingDay)
        val maxDate = closingDate.coerceAtMost(currentDate)

        form.value = AdvancePaymentForm(
            invoice = invoice,
            creditCard = creditCard,
            account = accountRepository.getDefaultAccount(),
            date = currentDate.coerceIn(openingDate, maxDate),
            currentBillAmount = calculateInvoiceUseCase(invoiceId),
            today = currentDate,
        )
    }

    val uiState = combine(
        form,
        accountRepository.observeAllAccounts(),
    ) { form, accounts ->
        when {
            form == null -> AdvancePaymentUiState.Loading
            form.invoice == null -> AdvancePaymentUiState.Error
            form.creditCard == null -> AdvancePaymentUiState.Error
            else -> AdvancePaymentUiState.Content(
                form = form,
                accounts = accounts,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AdvancePaymentUiState.Loading,
    )

    fun onAction(action: AdvancePaymentAction) {
        when (action) {
            is AdvancePaymentAction.SelectAccount -> {
                form.update { it?.copy(account = action.account) }
            }

            is AdvancePaymentAction.SelectDate -> {
                form.update { it?.copy(date = action.date) }
            }

            is AdvancePaymentAction.Submit -> submit(action.amount)
        }
    }

    private fun submit(amount: Double) = viewModelScope.launch {
        val form = form.value ?: return@launch
        val account = form.account ?: return@launch

        advanceInvoicePaymentUseCase(
            invoiceId = invoiceId,
            amount = amount,
            date = form.date,
            account = account,
        ).onLeft {
            crashlytics.recordException(it)
        }.onRight {
            analytics.logEvent(AdvanceInvoicePayment)
            modalManager.dismissAll()
        }
    }
}
