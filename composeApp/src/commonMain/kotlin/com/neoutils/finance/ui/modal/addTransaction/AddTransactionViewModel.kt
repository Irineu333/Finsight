@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.AddInstallmentTransactionsUseCase
import com.neoutils.finance.domain.usecase.CreateFutureInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val createFutureInvoiceUseCase: CreateFutureInvoiceUseCase,
    private val addInstallmentTransactionsUseCase: AddInstallmentTransactionsUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedInvoice = MutableStateFlow<Invoice?>(null)

    private val availableInvoicesFlow = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeAvailableInvoices(card.id)
        } else {
            flowOf(emptyList())
        }
    }.onEach { invoices ->
        selectedInvoice.value = invoices.firstOrNull { it.status.isOpen }
    }

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedCreditCard,
        availableInvoicesFlow,
        selectedInvoice,
    ) { categories, creditCards, selectedCard, availableInvoices, selectedInvoice ->
        AddTransactionUiState(
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
        initialValue = AddTransactionUiState(),
    )

    fun selectCreditCard(creditCard: CreditCard?) {
        selectedCreditCard.value = creditCard
    }

    fun selectInvoice(invoice: Invoice?) {
        selectedInvoice.value = invoice
    }

    fun addTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        form.build().onSuccess { transaction ->
            if (form.installments > 1 && form.invoice != null) {
                addInstallmentTransactionsUseCase(
                    baseTransaction = transaction,
                    totalInstallments = form.installments,
                    startingInvoice = form.invoice!!
                ).onSuccess {
                    modalManager.dismiss()
                }
            } else {
                transactionRepository.insert(transaction)
                modalManager.dismiss()
            }
        }
    }

    fun createFutureInvoice() = viewModelScope.launch {
        val creditCard = selectedCreditCard.value ?: return@launch
        createFutureInvoiceUseCase(creditCard).onSuccess { invoice ->
            selectedInvoice.value = invoice
        }
    }
}


