@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finance.ui.screen.invoiceTransactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.extension.combine
import com.neoutils.finance.util.DateFormats
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

private val currentDate
    get() = kotlin.time.Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class InvoiceTransactionsViewModel(
    creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
) : ViewModel() {

    private val formats = DateFormats()

    private val selectedInvoiceIndex = MutableStateFlow(0)

    private val filters = MutableStateFlow(
        InvoiceTransactionsFilters(
            category = null,
            type = null,
        )
    )

    private val creditCardFlow = creditCardRepository
        .observeCreditCardById(creditCardId = creditCardId)
        .filterNotNull()

    private val invoicesFlow = invoiceRepository
        .observeInvoicesByCreditCard(creditCardId = creditCardId)

    private val transactionsFlow = transactionRepository
        .observeTransactionsBy(creditCardId = creditCardId)

    val uiState = combine(
        creditCardFlow,
        invoicesFlow,
        transactionsFlow,
        categoryRepository.observeAllCategories(),
        selectedInvoiceIndex,
        filters,
    ) { creditCard, invoices, transactions, categories, index, currentFilters ->

        val invoice = invoices.getOrNull(index)

        val filteredTransactions = transactions
            .filter { it.invoice?.id == invoice?.id }
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        InvoiceTransactionsUiState(
            creditCardName = creditCard.name,
            invoices = invoices.map { invoice ->
                val invoiceTransactions = transactions.filter {
                    it.invoice?.id == invoice.id
                }

                val expense = invoiceTransactions
                    .filter { it.type == Transaction.Type.EXPENSE }
                    .sumOf { it.amount }

                val advancePayment = invoiceTransactions
                    .filter { it.type == Transaction.Type.ADVANCE_PAYMENT }
                    .sumOf { it.amount }

                val adjustment = invoiceTransactions
                    .filter { it.type == Transaction.Type.ADJUSTMENT }
                    .sumOf { it.amount }

                InvoiceTransactionsUiState.InvoiceSummary(
                    invoice = invoice,
                    expense = expense,
                    advancePayment = advancePayment,
                    adjustment = adjustment,
                    total = invoiceTransactions
                        .filterNot { it.type.isInvoicePayment }
                        .sumOf { it.creditAmount },
                    dueMonthLabel = formats.yearMonth.format(invoice.dueMonth),
                    periodLabel = "${formats.dayMonth.format(invoice.openingDate)} até ${formats.dayMonth.format(invoice.closingDate)}",
                    closingDate = invoice.closingDate,
                    isClosable = invoice.isClosable && currentDate >= invoice.closingDate,
                )
            },
            selectedInvoiceIndex = index,
            transactions = filteredTransactions,
            categories = categories,
            selectedCategory = currentFilters.category,
            selectedType = currentFilters.type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = InvoiceTransactionsUiState(
            selectedInvoiceIndex = selectedInvoiceIndex.value
        )
    )

    init {
        setInitialInvoice(creditCardId)
    }

    private fun setInitialInvoice(
        creditCardId: Long
    ) = viewModelScope.launch {
        val index = invoiceRepository
            .getInvoicesByCreditCard(creditCardId)
            .indexOfFirst { it.status.isOpen }

        if (index >= 0) {
            selectedInvoiceIndex.value = index
        }
    }

    fun onAction(action: InvoiceTransactionsAction) = viewModelScope.launch {
        when (action) {
            is InvoiceTransactionsAction.SelectInvoice -> {
                selectedInvoiceIndex.value = action.index.coerceAtLeast(0)
            }

            is InvoiceTransactionsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is InvoiceTransactionsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }
        }
    }
}

private data class InvoiceTransactionsFilters(
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
