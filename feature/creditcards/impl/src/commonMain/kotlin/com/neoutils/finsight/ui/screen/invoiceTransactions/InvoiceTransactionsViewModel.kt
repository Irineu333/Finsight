@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
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
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
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

    private val _events = Channel<InvoiceTransactionsEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private val creditCardFlow = creditCardRepository
        .observeCreditCardById(creditCardId = creditCardId)
        .onEach { if (it == null) _events.send(InvoiceTransactionsEvent.CreditCardDeleted) }
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
        // Invoice owed and its expense/advancePayment/adjustment breakdown, both derived
        // from the ledger (Σ liability-leg entries — task 4.11), not from legacy legs.
        val owedByInvoiceId = mutableMapOf<Long, Double>()
        val flowsByInvoiceId = mutableMapOf<Long, com.neoutils.finsight.domain.repository.InvoiceFlows>()
        for (inv in invoices) {
            owedByInvoiceId[inv.id] = entryRepository.invoiceOwed(inv.id)
            flowsByInvoiceId[inv.id] = entryRepository.invoiceFlows(inv.id)
        }

        val invoice = invoices.getOrNull(index)

        val invoiceTransactions = transactions
            .filter { transaction -> transaction.entries.any { it.invoiceId == invoice?.id } }
        val filteredTransactions = invoiceTransactions
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        InvoiceTransactionsUiState(
            creditCardName = creditCard.name,
            invoices = invoices.map { invoice ->
                val flows = flowsByInvoiceId.getValue(invoice.id)
                val expense = flows.expense
                val advancePayment = flows.advancePayment
                val adjustment = flows.adjustment

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
                    total = owedByInvoiceId.getValue(invoice.id),
                    dueMonth = invoice.dueMonth,
                    nextDateLabel = nextDateLabel,
                    closingDate = invoice.closingDate,
                    isClosable = invoice.isClosableOn(currentDate),
                )
            },
            selectedInvoiceIndex = index,
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
