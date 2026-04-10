package com.neoutils.finsight.ui.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.analytics.Analytics
import com.neoutils.finsight.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finsight.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
class EditInvoiceBalanceViewModel(
    private val initialInvoice: Invoice,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager,
    private val analytics: Analytics,
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

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

    private val currentBalance = selectedInvoice.map { invoice ->
        calculateInvoiceUseCase(invoice.id)
    }.stateIn(
        scope = viewModelScope,
        initialValue = null,
        started = SharingStarted.WhileSubscribed(5_000),
    )

    val uiState = combine(
        creditCards,
        selectedCreditCard,
        editableInvoices,
        selectedInvoice,
        currentBalance
    ) { cards, selectedCard, invoices, selectedInvoice, balance ->
        if (balance == null) {
            EditInvoiceBalanceUiState.Loading
        } else {
            EditInvoiceBalanceUiState.Content(
                creditCards = cards,
                selectedCreditCard = selectedCard,
                editableInvoices = invoices,
                selectedInvoice = selectedInvoice,
                currentBalance = balance
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

            is EditInvoiceBalanceAction.Submit -> {
                submit(action.targetBalance)
            }
        }
    }

    private fun submit(targetBalance: Double) = viewModelScope.launch {
        val invoice = selectedInvoice.value
        adjustInvoiceUseCase(
            invoice = invoice,
            target = targetBalance,
            adjustmentDate = currentDate
        )

        analytics.logEvent("adjust_invoice_balance")
        modalManager.dismiss()
    }
}
