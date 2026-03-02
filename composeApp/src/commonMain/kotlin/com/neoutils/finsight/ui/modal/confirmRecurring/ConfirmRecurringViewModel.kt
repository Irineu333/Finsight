package com.neoutils.finsight.ui.modal.confirmRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.usecase.ConfirmRecurringUseCase
import com.neoutils.finsight.domain.usecase.SkipRecurringUseCase
import com.neoutils.finsight.ui.component.ModalManager
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

class ConfirmRecurringViewModel(
    val recurring: Recurring,
    private val targetDate: LocalDate,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val confirmDate = MutableStateFlow(targetDate)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)
    private val invoices = MutableStateFlow<List<Invoice>>(emptyList())

    init {
        val creditCard = recurring.creditCard
        if (creditCard != null) {
            viewModelScope.launch {
                val allInvoices = invoiceRepository.getInvoicesByCreditCard(creditCard.id)
                invoices.value = allInvoices
                selectedInvoice.value = allInvoices.firstOrNull { it.status.isOpen }
            }
        }
    }

    val uiState = combine(confirmDate, selectedInvoice, invoices) { date, invoice, invoiceList ->
        ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = date,
            invoices = invoiceList,
            selectedInvoice = invoice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ConfirmRecurringUiState(
            recurring = recurring,
            confirmDate = targetDate,
        ),
    )

    fun onAction(action: ConfirmRecurringAction, amount: String = "") {
        when (action) {
            is ConfirmRecurringAction.DateChanged -> confirmDate.value = action.date
            is ConfirmRecurringAction.InvoiceSelected -> selectedInvoice.value = action.invoice
            is ConfirmRecurringAction.Confirm -> confirm(amount)
            is ConfirmRecurringAction.Skip -> skip()
        }
    }

    private fun confirm(amount: String) = viewModelScope.launch {
        val parsedAmount = amount.filter { it.isDigit() }.toLongOrNull()?.toDouble()?.div(100)
            ?: recurring.amount
        confirmRecurringUseCase(
            recurring = recurring,
            date = confirmDate.value,
            amount = parsedAmount,
            invoice = selectedInvoice.value,
        ).onRight { modalManager.dismiss() }
    }

    private fun skip() = viewModelScope.launch {
        skipRecurringUseCase(
            recurring = recurring,
            date = confirmDate.value,
        ).onRight { modalManager.dismiss() }
    }
}
