@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.CreditCardUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val entryRepository: IEntryRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val invoiceUiMapper: InvoiceUiMapper,
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
            invoices.groupBy { it.creditCard.id }
        }

    private val transactionsFlow = combine(
        selectedCard,
        invoicesFlow,
    ) { selectedCard, invoices ->
        invoices[selectedCard?.id]?.currentUnpaid()
    }.flatMapLatest { invoice ->
        if (invoice != null) {
            transactionRepository.observeTransactionsBy(invoiceId = invoice.id)
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
    ) { creditCards, transactions, invoices, categories, index, currentFilters ->
        if (creditCards.isEmpty()) {
            return@combine CreditCardsUiState.Empty
        }

        val filteredTransactions = transactions
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        val cards = creditCards.map { creditCard ->
            val cardInvoices = invoices[creditCard.id].orEmpty()
            val invoice = cardInvoices.currentUnpaid()
            val ui = CreditCardUi(
                cardId = creditCard.id,
                iconKey = creditCard.iconKey,
                name = creditCard.name,
                closingDay = creditCard.closingDay,
                dueDay = creditCard.dueDay,
                limit = creditCard.limit,
                invoiceUi = invoice?.let {
                    invoiceUiMapper.toUi(invoice = it, cardInvoices = cardInvoices)
                },
                hasMovement = entryRepository.hasEntries(creditCard.accountId),
            )
            Triple(creditCard, invoice, ui)
        }

        CreditCardsUiState.Content(
            creditCards = cards.map { it.third },
            domainCards = cards.map { it.first },
            domainInvoices = cards.map { it.second },
            selectedCardIndex = index,
            transactions = filteredTransactions,
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

    // The card surfaces its oldest unpaid invoice — the bill most in need of attention.
    // Mirrors the previous associateBy over the DESC-ordered unpaid list (last wins).
    private fun List<Invoice>.currentUnpaid(): Invoice? = minByOrNull { it.openingMonth }

    fun onAction(action: CreditCardsAction) = viewModelScope.launch {
        when (action) {
            is CreditCardsAction.SelectCard -> {
                selectedCardId.value = creditCardRepository
                    .getAllCreditCards()
                    .getOrNull(action.index.coerceAtLeast(0))
                    ?.id
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
    val type: TransactionType?,
    val recurringOnly: Boolean,
    val installmentOnly: Boolean,
)

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { transaction ->
        transaction.category?.id == category.id
    }
}

private fun List<Transaction>.filter(type: TransactionType?): List<Transaction> {
    if (type == null) return this
    // The card's own leg is what this screen shows, so the filter reads its
    // direction — a payment credits the card, a purchase debits it.
    return filter { transaction ->
        transaction.entries
            .firstOrNull { it.account.type == AccountType.LIABILITY }
            ?.let { deriveTransactionType(it.amount, transaction.entries) } == type
    }
}

private fun List<Transaction>.filter(recurringOnly: Boolean): List<Transaction> {
    if (!recurringOnly) return this
    return filter { transaction -> transaction.recurring != null }
}

private fun List<Transaction>.filterInstallment(installmentOnly: Boolean): List<Transaction> {
    if (!installmentOnly) return this
    return filter { transaction -> transaction.installment != null }
}
