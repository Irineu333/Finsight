@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.feature.creditCards.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.core.utils.extension.combine
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.mapper.IInvoiceUiMapper
import com.neoutils.finsight.feature.creditCards.model.CreditCardUi
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.mapper.IOperationUiMapper
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val creditCardRepository: ICreditCardRepository,
    private val operationRepository: IOperationRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceUiMapper: IInvoiceUiMapper,
    private val operationUiMapper: IOperationUiMapper,
    private val initialCreditCardId: Long? = null,
) : ViewModel() {

    private val creditCards = creditCardRepository.observeAllCreditCards()

    private val selectedCardId = MutableStateFlow(initialCreditCardId)

    private val selectedCardIndex = combine(
        creditCards,
        selectedCardId,
    ) { creditCards, selectedCardId ->
        creditCards.indexOfFirst {
            it.id == selectedCardId
        }.coerceAtLeast(minimumValue = 0)
    }

    private val selectedCard = combine(
        creditCards,
        selectedCardIndex,
    ) { creditCards, index ->
        creditCards.getOrNull(index)
    }

    private val filters = MutableStateFlow(
        CreditCardsFilters(
            category = null,
            type = null,
            recurringOnly = false,
            installmentOnly = false,
        )
    )

    private val invoicesFlow = invoiceRepository
        .observeUnpaidInvoices()
        .map { invoices ->
            invoices.associateBy { it.creditCardId }
        }

    private val operationsUiFlow = combine(
        selectedCard,
        invoicesFlow,
    ) { selectedCard, invoices ->
        selectedCard?.id to invoices[selectedCard?.id]
    }.flatMapLatest { (cardId, invoice) ->
        when {
            invoice == null -> flowOf(emptyList())
            cardId == null -> flowOf(emptyList())
            else -> {
                operationRepository.observeOperationsBy(
                    invoiceId = invoice.id,
                ).map { operations ->
                    operationUiMapper.toUi(
                        operations = operations,
                        perspective = OperationPerspective.Card(
                            creditCardId = cardId,
                        ),
                    )
                }
            }
        }
    }

    val uiState = combine(
        creditCards,
        operationsUiFlow,
        invoicesFlow,
        categoryRepository.observeAllCategories(),
        selectedCardIndex,
        filters,
    ) { creditCards, operationsUi, invoices, categories, index, currentFilters ->
        if (creditCards.isEmpty()) {
            return@combine CreditCardsUiState.Empty
        }

        val filteredOperations = operationsUi
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)
            .sortedByDescending { it.operation.date }
            .groupBy { it.operation.date }

        CreditCardsUiState.Content(
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
            showRecurringOnly = currentFilters.recurringOnly,
            showInstallmentOnly = currentFilters.installmentOnly,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CreditCardsUiState.Loading,
    )

    fun onAction(action: CreditCardsAction) = viewModelScope.launch {
        when (action) {
            is CreditCardsAction.SelectCard -> {
                selectedCardId.value = creditCardRepository
                    .getAllCreditCards()
                    .getOrNull(action.index.coerceAtLeast(0))?.id
            }

            is CreditCardsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is CreditCardsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is CreditCardsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            is CreditCardsAction.ToggleInstallment -> {
                filters.value = filters.value.copy(installmentOnly = action.enabled)
            }
        }
    }
}

private data class CreditCardsFilters(
    val category: Category?,
    val type: Transaction.Type?,
    val recurringOnly: Boolean,
    val installmentOnly: Boolean,
)

private fun List<OperationUi>.filter(category: Category?): List<OperationUi> {
    if (category == null) return this
    return filter { operationUi ->
        val operation = operationUi.operation
        operation.categoryId == category.id || operation.primaryTransaction.categoryId == category.id
    }
}

private fun List<OperationUi>.filter(type: Transaction.Type?): List<OperationUi> {
    if (type == null) return this
    return filter { it.operation.type == type }
}

private fun List<OperationUi>.filter(recurringOnly: Boolean): List<OperationUi> {
    if (!recurringOnly) return this
    return filter { it.operation.recurring != null }
}

private fun List<OperationUi>.filterInstallment(installmentOnly: Boolean): List<OperationUi> {
    if (!installmentOnly) return this
    return filter { it.operation.installment != null }
}
