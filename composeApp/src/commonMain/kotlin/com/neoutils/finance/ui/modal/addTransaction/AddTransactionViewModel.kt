package com.neoutils.finance.ui.modal.addTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.usecase.GetOrCreateCurrentInvoiceUseCase
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class AddTransactionViewModel(
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val getOrCreateCurrentInvoiceUseCase: GetOrCreateCurrentInvoiceUseCase,
    private val modalManager: ModalManager
) : ViewModel() {

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards()
    ) { categories, creditCards ->
        AddTransactionUiState(
            incomeCategories = categories.filter { it.type.isIncome },
            expenseCategories = categories.filter { it.type.isExpense },
            creditCards = creditCards
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AddTransactionUiState(),
    )

    suspend fun getOpenInvoiceForCard(creditCardId: Long): Invoice? {
        return invoiceRepository.getOpenInvoice(creditCardId)
    }

    fun addTransaction(
        transaction: Transaction
    ) = viewModelScope.launch {

        if (transaction.target.isCreditCard && transaction.creditCard == null) return@launch

        val transactionWithInvoice = if (transaction.target.isCreditCard && transaction.creditCard != null) {
            val invoice = getOrCreateCurrentInvoiceUseCase(transaction.creditCard.id)
                ?: return@launch  // Cartão foi deletado

            if (invoice.status != Invoice.Status.OPEN) {
                return@launch
            }

            transaction.copy(invoice = invoice)
        } else {
            transaction
        }

        transactionRepository.insert(transactionWithInvoice)
        modalManager.dismiss()
    }
}


