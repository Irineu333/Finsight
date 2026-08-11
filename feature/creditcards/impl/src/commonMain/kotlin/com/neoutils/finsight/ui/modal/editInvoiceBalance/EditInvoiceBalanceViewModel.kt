package com.neoutils.finsight.ui.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.error.ClosedAccountException
import com.neoutils.finsight.domain.error.InvoiceException
import com.neoutils.finsight.domain.error.UnbalancedTransactionException
import com.neoutils.finsight.domain.error.toUiText
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.window
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.analytics.event.AdjustInvoiceBalance
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.ledger_action_error_generic
import com.neoutils.finsight.ui.component.ModalManager
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonthYear
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EditInvoiceBalanceViewModel(
    private val initialInvoice: Invoice,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = clock.today(timeZone)

    private val selectedCreditCard = MutableStateFlow(initialInvoice.creditCard)

    private val editableInvoices = selectedCreditCard.map { creditCard ->
        invoiceRepository
            .getInvoicesByCreditCard(creditCard.id)
            .filter { it.status.isEditable }
    }

    private val creditCards = flow {
        emit(creditCardRepository.getAllCreditCards())
    }

    private val selectedInvoice = MutableStateFlow(initialInvoice)

    // The date as the form holds it: text, because that is what the field edits.
    private val date = MutableStateFlow(dayMonthYear.format(dateInWindowOf(initialInvoice)))

    private val currentBalance = selectedInvoice.map { invoice ->
        calculateInvoiceUseCase(invoice)
    }.stateIn(
        scope = viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(5_000),
    )

    init {
        // The invoice governs the date. This collector reads the selected invoice and
        // never the date itself, which is what makes the reverse direction impossible
        // rather than merely avoided.
        viewModelScope.launch {
            selectedInvoice.drop(1).collect { invoice ->
                date.value = dayMonthYear.format(dateInWindowOf(invoice, date.value))
            }
        }
    }

    /**
     * Today's day placed in [invoice]'s window, capped at today.
     *
     * The day is what is preserved and the window decides the month, exactly as the other
     * card forms do. An adjustment is not a purchase, so the invoice's closing is no
     * ceiling here — only today is (design D3).
     */
    private fun dateInWindowOf(invoice: Invoice, from: String? = null): LocalDate {
        val today = currentDate
        val day = from?.let { runCatching { dayMonthYear.parse(it) }.getOrNull()?.day } ?: today.day
        return invoice.window.dateOn(day).coerceAtMost(today)
    }

    val uiState = combine(
        creditCards,
        selectedCreditCard,
        editableInvoices,
        selectedInvoice,
        currentBalance,
        date,
    ) { cards, selectedCard, invoices, selectedInvoice, balance, date ->
        if (balance == null) {
            EditInvoiceBalanceUiState.Loading
        } else {
            EditInvoiceBalanceUiState.Content(
                creditCards = cards,
                selectedCreditCard = selectedCard,
                editableInvoices = invoices,
                selectedInvoice = selectedInvoice,
                currentBalance = balance,
                currency = accountRepository.currencyOf(selectedCard),
                date = date,
                today = currentDate,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditInvoiceBalanceUiState.Loading
    )

    fun onAction(action: EditInvoiceBalanceAction) = viewModelScope.launch {
        when (action) {
            is EditInvoiceBalanceAction.SelectCreditCard -> {
                selectedCreditCard.value = action.creditCard

                selectedInvoice.value = invoiceRepository
                    .getOpenInvoice(action.creditCard.id) ?: return@launch
            }

            is EditInvoiceBalanceAction.SelectInvoice -> {
                selectedInvoice.value = action.invoice
            }

            is EditInvoiceBalanceAction.ChangeDate -> {
                date.value = action.date
            }

            is EditInvoiceBalanceAction.Submit -> {
                submit(action.targetBalance)
            }
        }
    }

    private fun submit(targetBalance: Double) = viewModelScope.launch {
        val on = runCatching { dayMonthYear.parse(date.value) }.getOrNull() ?: return@launch

        adjustInvoiceUseCase(
            invoice = selectedInvoice.value,
            target = targetBalance,
            adjustmentDate = on
        ).onLeft {
            when (it) {
                // No change to make: the target equals the current balance. Close
                // quietly — nothing failed.
                is InvoiceNotAdjustedException -> modalManager.dismiss()
                else -> {
                    crashlytics.recordException(it)
                    modalManager.showError(it.toUiMessage())
                }
            }
        }.onRight {
            analytics.logEvent(AdjustInvoiceBalance)
            modalManager.dismiss()
        }
    }

    private fun Throwable.toUiMessage(): UiText = when (this) {
        is ClosedAccountException -> error.toUiText()
        is InvoiceException -> error.toUiText()
        is UnbalancedTransactionException -> error.toUiText()
        else -> UiText.Res(Res.string.ledger_action_error_generic)
    }
}
