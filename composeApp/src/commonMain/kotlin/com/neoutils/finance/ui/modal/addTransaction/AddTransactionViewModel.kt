@file:OptIn(ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.model.form.TransactionForm
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.usecase.AddTransactionUseCase
import com.neoutils.finance.ui.component.ModalManager
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val addTransactionUseCase: AddTransactionUseCase,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val modalManager: ModalManager
) : ViewModel() {

    private val selectedCreditCard = MutableStateFlow<CreditCard?>(null)

    private val currentInvoiceUi = selectedCreditCard.flatMapLatest { card ->
        if (card != null) {
            invoiceRepository.observeOpenInvoice(card.id)
        } else {
            flowOf(null)
        }
    }.mapLatest { invoice ->
        invoice?.let { invoiceUiMapper.toUi(it) }
    }

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedCreditCard,
        currentInvoiceUi
    ) { categories, creditCards, selectedCard, invoiceUi ->
        AddTransactionUiState(
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards,
            selectedCreditCard = selectedCard,
            currentInvoiceUi = invoiceUi
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddTransactionUiState(),
    )

    fun selectCreditCard(creditCard: CreditCard?) {
        selectedCreditCard.value = creditCard
    }

    fun addTransaction(
        form: TransactionForm
    ) = viewModelScope.launch {
        addTransactionUseCase(form).onSuccess {
            modalManager.dismiss()
        }
    }
}


