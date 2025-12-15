package com.neoutils.finance.ui.modal.editTransaction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.ui.component.ModalManager
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class EditTransactionViewModel(
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val modalManager: ModalManager
) : ViewModel() {

    val uiState = combine(
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards()
    ) { categories, creditCards ->
        EditTransactionUiState(
            incomeCategories = categories.filter { it.type == Category.Type.INCOME },
            expenseCategories = categories.filter { it.type == Category.Type.EXPENSE },
            creditCards = creditCards
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = EditTransactionUiState()
    )

    fun updateTransaction(
        transaction: Transaction
    ) = viewModelScope.launch {
        if (transaction.target.isCreditCard && transaction.creditCard == null) return@launch

        transactionRepository.update(transaction)
        modalManager.dismiss()
    }
}
