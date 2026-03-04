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
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock

class ConfirmRecurringViewModel(
    val recurring: Recurring,
    private val targetDate: LocalDate,
    private val invoiceRepository: IInvoiceRepository,
    private val confirmRecurringUseCase: ConfirmRecurringUseCase,
    private val skipRecurringUseCase: SkipRecurringUseCase,
    private val modalManager: ModalManager,
) : ViewModel() {

    private val currentDate get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    private val confirmDate = MutableStateFlow(targetDate.takeIf { it <= currentDate } ?: currentDate)
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
            confirmDate = targetDate.takeIf { it <= currentDate } ?: currentDate,
        ),
    )

    fun onAction(action: ConfirmRecurringAction, amount: String = "") {
        when (action) {
            is ConfirmRecurringAction.DateChanged -> {
                confirmDate.value = action.date.takeIf { it <= currentDate } ?: currentDate
            }
            is ConfirmRecurringAction.InvoiceSelected -> selectedInvoice.value = action.invoice
            is ConfirmRecurringAction.Confirm -> confirm(amount)
            is ConfirmRecurringAction.Skip -> skip()
        }
    }

    private fun confirm(amount: String) = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate
        val parsedAmount = amount.filter { it.isDigit() }.toLongOrNull()?.toDouble()?.div(100)
            ?: recurring.amount
        confirmRecurringUseCase(
            recurring = recurring,
            date = date,
            amount = parsedAmount,
            invoice = selectedInvoice.value,
        ).onRight { modalManager.dismiss() }
    }

    private fun skip() = viewModelScope.launch {
        val date = confirmDate.value.takeIf { it <= currentDate } ?: currentDate
        skipRecurringUseCase(
            recurring = recurring,
            date = date,
        ).onRight { modalManager.dismiss() }
    }
}
