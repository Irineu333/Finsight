@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.InvoiceMonthSelection
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.AddInstallmentTransactionsUseCase
import com.neoutils.finance.domain.usecase.BuildTransactionUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.YearMonth

class AddTransactionViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val buildTransactionUseCase: BuildTransactionUseCase,
    private val addInstallmentTransactionsUseCase: AddInstallmentTransactionsUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)
    private val selectedDueMonth = MutableStateFlow<YearMonth?>(null)

    private val _errorMessage = MutableSharedFlow<String>()
    val errorMessage = _errorMessage.asSharedFlow()

    private val invoicesFlow = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeInvoicesByCreditCard(card.id)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedCreditCard,
        invoicesFlow,
        selectedDueMonth,
    ) { categories, creditCards, selectedCard, invoices, dueMonth ->
        AddTransactionUiState(
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
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddTransactionUiState(),
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

    fun addTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        buildTransactionUseCase(form).onSuccess { transaction ->
            if (form.installments > 1 && transaction.invoice != null) {
                addInstallmentTransactionsUseCase(
                    baseTransaction = transaction,
                    totalInstallments = form.installments,
                    startingInvoice = transaction.invoice
                ).onSuccess {
                    modalManager.dismiss()
                }.onFailure { error ->
                    _errorMessage.emit(error.message ?: "Erro ao adicionar parcelas")
                }
            } else {
                transactionRepository.insert(transaction)
                modalManager.dismiss()
            }
        }
    }
}
