@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val creditCardRepository: ICreditCardRepository,
    private val operationRepository: IOperationRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val initialCreditCardId: Long? = null,
) : ViewModel() {

    private val creditCards = creditCardRepository.observeAllCreditCards()

    private val selectedCardIndex = MutableStateFlow(
        runBlocking {
            creditCards.first().indexOfFirst {
                it.id == initialCreditCardId
            }.coerceAtLeast(minimumValue = 0)
        }
    )

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
        creditCards,
        invoicesFlow,
        selectedCardIndex,
    ) { creditCards, invoices, index ->
        invoices[creditCards.getOrNull(index)?.id]
    }.flatMapLatest { invoice ->
        if (invoice != null) {
            operationRepository.observeOperationsBy(invoiceId = invoice.id)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState = combine(
        creditCards,
        transactionsFlow,
        invoicesFlow,
        categoryRepository.observeAllCategories(),
        selectedCardIndex,
        filters,
    ) { creditCards, operations, invoices, categories, index, currentFilters ->

        val filteredOperations = operations
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
            operations = filteredOperations,
            categories = categories,
            selectedCategory = currentFilters.category,
            selectedType = currentFilters.type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState(
            selectedCardIndex = selectedCardIndex.value,
        )
    )

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

private fun List<Operation>.filter(category: Category?): List<Operation> {
    if (category == null) return this
    return filter { operation ->
        operation.category?.id == category.id || operation.primaryTransaction.category?.id == category.id
    }
}

private fun List<Operation>.filter(type: Transaction.Type?): List<Operation> {
    if (type == null) return this
    return filter { operation -> operation.type == type }
}