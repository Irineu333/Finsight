@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditTransactionViewModel(
    private val transaction: Transaction,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow(transaction.creditCard)
    private val selectedInvoice = MutableStateFlow(transaction.invoice)

    private val availableInvoicesFlow = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeAvailableInvoices(card.id)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedCreditCard,
        availableInvoicesFlow,
        selectedInvoice,
    ) { categories, creditCards, selectedCard, availableInvoices, selectedInvoice ->
        EditTransactionUiState(
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            availableInvoices = availableInvoices,
            selectedInvoice = selectedInvoice,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditTransactionUiState(
            selectedCreditCard = transaction.creditCard,
            selectedInvoice = transaction.invoice
        )
    )

    fun selectCreditCard(creditCard: CreditCard?) = viewModelScope.launch {
        selectedCreditCard.value = creditCard
        selectedInvoice.value = creditCard?.let {
            invoiceRepository
                .getInvoicesByCreditCard(creditCard.id)
                .firstOrNull { it.status.isOpen }
        }
    }

    fun selectInvoice(invoice: Invoice?) {
        selectedInvoice.value = invoice
    }

    fun createFutureInvoice() = viewModelScope.launch {
        val creditCard = selectedCreditCard.value ?: return@launch
        createFutureInvoiceUseCase(creditCard).onSuccess { invoice ->
            selectedInvoice.value = invoice
        }
    }

    fun updateTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        form.build(id = transaction.id).onSuccess {
            transactionRepository.update(it)
            modalManager.dismissAll()
        }
    }
}

