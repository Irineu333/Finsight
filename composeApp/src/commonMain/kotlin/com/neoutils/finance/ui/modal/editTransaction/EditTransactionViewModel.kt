@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.InvoiceMonthSelection
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.GetOrCreateInvoiceForMonthUseCase
import com.neoutils.finance.extension.combine
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val getOrCreateInvoiceForMonthUseCase: GetOrCreateInvoiceForMonthUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow(transaction.creditCard)
    private val selectedDueMonth = MutableStateFlow(transaction.invoice?.dueMonth)

    private val invoicesFlow = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeInvoicesByCreditCard(card.id)
        } else {
            flowOf(emptyList())
        }
    }

    private val openInvoiceFlow = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeOpenInvoice(card.id)
        } else {
            flowOf(null)
        }
    }

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedCreditCard,
        invoicesFlow,
        selectedDueMonth,
        openInvoiceFlow,
    ) { categories, creditCards, selectedCard, invoices, dueMonth, openInvoice ->
        EditTransactionUiState(
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            invoiceSelection = dueMonth?.let { month ->
                InvoiceMonthSelection(
                    dueMonth = month,
                    existingInvoice = invoices.find { it.dueMonth == month }
                )
            },
            minDueMonth = openInvoice?.dueMonth,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditTransactionUiState(
            selectedCreditCard = transaction.creditCard,
            invoiceSelection = transaction.invoice?.let {
                InvoiceMonthSelection(
                    dueMonth = it.dueMonth,
                    existingInvoice = it
                )
            },
        )
    )

    fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        selectedCreditCard.value = creditCard
        selectedDueMonth.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
                ?.dueMonth
        }
    }

    fun navigateToMonth(dueMonth: YearMonth) {
        selectedDueMonth.value = dueMonth
    }

    fun updateTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        val creditCard = form.creditCard
        val dueMonth = selectedDueMonth.value

        val invoice = if (creditCard != null && dueMonth != null && form.target.isCreditCard) {
            getOrCreateInvoiceForMonthUseCase(creditCard, dueMonth).getOrElse {
                return@launch
            }
        } else {
            null
        }

        val updatedForm = form.copy(invoice = invoice)

        updatedForm.build(id = transaction.id).onSuccess {
            transactionRepository.update(it)
            modalManager.dismissAll()
        }
    }
}
