@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.creditCards.screen.invoiceTransactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.repository.IInvoiceRepository
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.core.utils.extension.combine
import com.neoutils.finsight.feature.transactions.extension.signedImpact
import com.neoutils.finsight.core.utils.extension.safeOnDay
import com.neoutils.finsight.feature.creditCards.resources.*
import com.neoutils.finsight.core.ui.util.UiText
import com.neoutils.finsight.core.utils.util.dayMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import com.neoutils.finsight.feature.categories.model.Category
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.transactions.mapper.IOperationUiMapper
import com.neoutils.finsight.feature.transactions.model.OperationPerspective
import com.neoutils.finsight.feature.transactions.model.OperationUi
import com.neoutils.finsight.feature.transactions.model.Transaction

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class InvoiceTransactionsViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
    private val operationUiMapper: IOperationUiMapper,
) : ViewModel() {

    private val selectedInvoiceIndex = MutableStateFlow(0)

    private val filters = MutableStateFlow(
        InvoiceTransactionsFilters(
            category = null,
            type = null,
            recurringOnly = false,
            installmentOnly = false,
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

        val invoiceOperations = operations
            .filter { it.targetInvoiceId == invoice?.id || it.transactions.any { tx -> tx.invoiceId == invoice?.id } }
        val invoiceOperationsUi = operationUiMapper.toUi(
            operations = invoiceOperations,
            perspective = OperationPerspective.Card(creditCardId = creditCardId),
        )
        val filteredOperations = invoiceOperationsUi
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)
            .sortedByDescending { it.operation.date }
            .groupBy { it.operation.date }

        InvoiceTransactionsUiState(
            creditCardName = creditCard.name,
            creditCard = creditCard,
            invoices = invoices.map { invoice ->
                val invoiceTransactions = transactions.filter {
                    it.invoiceId == invoice.id && it.target == Transaction.Target.CREDIT_CARD
                }

                val expense = invoiceTransactions
                    .filter { it.type == Transaction.Type.EXPENSE }
                    .sumOf { it.amount }

                val advancePayment = invoiceTransactions
                    .filter { it.type == Transaction.Type.INCOME && it.target == Transaction.Target.CREDIT_CARD && it.isInvoicePayment }
                    .sumOf { it.amount }

                val adjustment = invoiceTransactions
                    .filter { it.type == Transaction.Type.ADJUSTMENT }
                    .sumOf { it.amount }

                val openingDate = invoice.openingMonth.safeOnDay(creditCard.closingDay)
                val closingDate = invoice.closingMonth.safeOnDay(creditCard.closingDay)
                val dueDate = invoice.dueMonth.safeOnDay(creditCard.dueDay)

                val nextDateLabel = when (invoice.status) {
                    Invoice.Status.OPEN -> UiText.ResWithArgs(
                        Res.string.invoice_closes_on,
                        dayMonth.format(closingDate)
                    )

                    Invoice.Status.CLOSED -> UiText.ResWithArgs(
                        Res.string.invoice_due_on,
                        dayMonth.format(dueDate)
                    )

                    Invoice.Status.PAID -> invoice.paidAt?.let { paidDate ->
                        UiText.ResWithArgs(
                            Res.string.invoice_paid_on,
                            dayMonth.format(paidDate)
                        )
                    }

                    Invoice.Status.FUTURE -> UiText.ResWithArgs(
                        Res.string.invoice_opens_on,
                        dayMonth.format(openingDate)
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
                    closingDate = closingDate,
                    isClosable = invoice.isClosable && currentDate >= closingDate,
                )
            },
            selectedInvoiceIndex = index,
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

            is InvoiceTransactionsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            is InvoiceTransactionsAction.ToggleInstallment -> {
                filters.value = filters.value.copy(installmentOnly = action.enabled)
            }
        }
    }
}

private data class InvoiceTransactionsFilters(
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
