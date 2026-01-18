@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finance.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.combine
import com.neoutils.finance.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val initialCreditCardId: Long? = null
) : ViewModel() {

    private val selectedCardIndex = MutableStateFlow(0)

    private val filters = MutableStateFlow(
        CreditCardsFilters(
            category = null,
            type = null,
        )
    )

    private val invoicesFlow = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices ->
            invoices.associateBy { it.creditCard.id }
        }

    private val transactionsFlow = combine(
        creditCardRepository.observeAllCreditCards(),
        invoicesFlow,
        selectedCardIndex,
    ) { creditCards, invoices, index ->
        invoices[creditCards.getOrNull(index)?.id]
    }.flatMapLatest { invoice ->
        if (invoice != null) {
            transactionRepository.observeTransactionsBy(invoiceId = invoice.id)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState = combine(
        creditCardRepository.observeAllCreditCards(),
        transactionsFlow,
        invoicesFlow,
        categoryRepository.observeAllCategories(),
        selectedCardIndex,
        filters,
    ) { creditCards, transactions, invoices, categories, index, currentFilters ->

        val filteredTransactions = transactions
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        CreditCardsUiState(
            creditCards = creditCards.map { creditCard ->
                val invoice = invoices[creditCard.id]
                CreditCardUi(
                    creditCard = creditCard,
                    invoiceUi = invoice?.let {
                        invoiceUiMapper.toUi(invoice = invoice)
                    }
                )
            },
            selectedCardIndex = index,
            transactions = filteredTransactions,
            categories = categories,
            selectedCategory = currentFilters.category,
            selectedType = currentFilters.type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState()
    )

    init {
        initialCreditCardId?.let {
            setInitialCreditCard(creditCardId = it)
        }
    }

    private fun setInitialCreditCard(
        creditCardId: Long
    ) = viewModelScope.launch {
        val index = creditCardRepository
            .getAllCreditCards()
            .indexOfFirst { it.id == creditCardId }

        if (index >= 0) {
            selectedCardIndex.value = index
        }
    }

    fun onAction(action: CreditCardsAction) = viewModelScope.launch {
        when (action) {
            is CreditCardsAction.SelectCard -> {
                selectedCardIndex.value = action.index.coerceAtLeast(0)
            }

            is CreditCardsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is CreditCardsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }
        }
    }
}

private data class CreditCardsFilters(
    val category: Category?,
    val type: Transaction.Type?,
)

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.category?.id == category.id }
}

private fun List<Transaction>.filter(type: Transaction.Type?): List<Transaction> {
    if (type == null) return this
    return filter { it.type == type }
}