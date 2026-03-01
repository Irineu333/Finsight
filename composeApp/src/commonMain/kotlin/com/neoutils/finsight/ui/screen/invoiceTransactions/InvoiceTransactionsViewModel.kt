@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class InvoiceTransactionsViewModel(
    creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
) : ViewModel() {

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

    private val operationsFlow = operationRepository
        .observeOperationsBy(creditCardId = creditCardId)

    val uiState = combine(
        creditCardFlow,
        invoicesFlow,
        operationsFlow,
        categoryRepository.observeAllCategories(),
        selectedInvoiceIndex,
        filters,
    ) { creditCard, invoices, operations, categories, index, currentFilters ->
        val transactions = operations.flatMap { it.transactions }

        val invoice = invoices.getOrNull(index)

        val filteredOperations = operations
            .filter { it.targetInvoice?.id == invoice?.id || it.transactions.any { tx -> tx.invoice?.id == invoice?.id } }
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        InvoiceTransactionsUiState(
            creditCardName = creditCard.name,
            invoices = invoices.map { invoice ->
                val invoiceTransactions = transactions.filter {
                    it.invoice?.id == invoice.id && it.target == Transaction.Target.CREDIT_CARD
                }

                val expense = invoiceTransactions
                    .filter { it.type == Transaction.Type.EXPENSE }
                    .sumOf { it.amount }

                val advancePayment = invoiceTransactions
                    .filter { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.CREDIT_CARD && it.title == "Pagamento de Fatura" }
                    .sumOf { it.amount }

                val adjustment = invoiceTransactions
                    .filter { it.type == Transaction.Type.ADJUSTMENT }
                    .sumOf { it.amount }

                val nextDateLabel = when (invoice.status) {
                    Invoice.Status.OPEN -> UiText.ResWithArgs(
                        Res.string.invoice_closes_on,
                        dayMonth.format(invoice.closingDate)
                    )

                    Invoice.Status.CLOSED -> UiText.ResWithArgs(
                        Res.string.invoice_due_on,
                        dayMonth.format(invoice.dueDate)
                    )

                    Invoice.Status.PAID -> invoice.paidAt?.let { paidDate ->
                        UiText.ResWithArgs(
                            Res.string.invoice_paid_on,
                            dayMonth.format(paidDate)
                        )
                    }

                    Invoice.Status.FUTURE -> UiText.ResWithArgs(
                        Res.string.invoice_opens_on,
                        dayMonth.format(invoice.openingDate)
                    )

                    Invoice.Status.RETROACTIVE -> null
                }

                InvoiceTransactionsUiState.InvoiceSummary(
                    invoice = invoice,
                    expense = expense,
                    advancePayment = advancePayment,
                    adjustment = adjustment,
                    total = invoiceTransactions.sumOf { -it.signedImpact() },
                    dueMonth = invoice.dueMonth,
                    nextDateLabel = nextDateLabel,
                    closingDate = invoice.closingDate,
                    isClosable = invoice.isClosable && currentDate >= invoice.closingDate,
                )
            },
            selectedInvoiceIndex = index,
            operations = filteredOperations,
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
