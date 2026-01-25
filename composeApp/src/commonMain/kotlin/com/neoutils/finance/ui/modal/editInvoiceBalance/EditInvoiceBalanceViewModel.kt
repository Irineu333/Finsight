package com.neoutils.finance.ui.modal.editInvoiceBalance

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.usecase.AdjustInvoiceUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

data class EditInvoiceBalanceUiState(
    val creditCards: List<CreditCard> = emptyList(),
    val selectedCreditCard: CreditCard? = null,
    val editableInvoices: List<Invoice> = emptyList(),
    val selectedInvoice: Invoice? = null,
    val currentBalance: Double = 0.0,
)

@OptIn(ExperimentalTime::class)
class EditInvoiceBalanceViewModel(
    private val initialInvoice: Invoice,
    private val adjustInvoiceUseCase: AdjustInvoiceUseCase,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val invoiceRepository: IInvoiceRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    private val timeZone get() = TimeZone.currentSystemDefault()
    private val currentDate get() = Clock.System.now().toLocalDateTime(timeZone).date

    private val selectedCreditCard = MutableStateFlow(initialInvoice.creditCard)

    private val editableInvoices = flow {
        emit(
            invoiceRepository
                .getInvoicesByCreditCard(initialInvoice.creditCard.id)
                .filter { it.status.isEditable }
        )
    }

    private val creditCards = flow {
        emit(creditCardRepository.getAllCreditCards())
    }

    private val selectedInvoice = MutableStateFlow(initialInvoice)

    private val currentBalance = selectedInvoice.map { invoice ->
        calculateInvoiceUseCase(invoice.id)
    }.stateIn(
        scope = viewModelScope,
        initialValue = runBlocking {
            calculateInvoiceUseCase(initialInvoice.id)
        },
        started = SharingStarted.WhileSubscribed(5_000),
    )

    val uiState = combine(
        creditCards,
        selectedCreditCard,
        editableInvoices,
        selectedInvoice,
        currentBalance
    ) { cards, selectedCard, invoices, selectedInvoice, balance ->
        EditInvoiceBalanceUiState(
            creditCards = cards,
            selectedCreditCard = selectedCard,
            editableInvoices = invoices,
            selectedInvoice = selectedInvoice,
            currentBalance = balance
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditInvoiceBalanceUiState(
            currentBalance = currentBalance.value,
            selectedInvoice = selectedInvoice.value,
            selectedCreditCard = selectedCreditCard.value,
        )
    )

    fun selectCreditCard(creditCard: CreditCard) = viewModelScope.launch {
        selectedCreditCard.value = creditCard
        selectedInvoice.value = invoiceRepository
            .getOpenInvoice(creditCard.id) ?: return@launch
    }

    fun selectInvoice(invoice: Invoice) {
        selectedInvoice.value = invoice
    }

    fun adjustBalance(targetBalance: Double) = viewModelScope.launch {
        adjustInvoiceUseCase(
            invoice = selectedInvoice.value,
            target = targetBalance,
            adjustmentDate = currentDate
        )

        modalManager.dismiss()
    }
}
