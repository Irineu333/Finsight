@file:OptIn(ExperimentalTime::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.invoiceTransactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.*
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.usecase.UnarchiveCreditCardUseCase
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.retireActionOf
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.ui.model.legUnder
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.resources.*
import com.neoutils.finsight.util.UiText
import com.neoutils.finsight.util.dayMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.neoutils.finsight.extension.today
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class InvoiceTransactionsViewModel(
    private val creditCardId: Long,
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
    private val unarchiveCreditCard: UnarchiveCreditCardUseCase,
    private val crashlytics: Crashlytics,
    private val clock: Clock,
) : ViewModel() {

    private val currentDate get() = clock.today()

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

    // A transaction of this card is one with a leg on the card's LIABILITY account.
    private val transactionsFlow = creditCardFlow
        .flatMapLatest { creditCard ->
            transactionRepository.observeTransactionsBy(accountId = creditCard.accountId)
        }

    val uiState = combine(
        creditCardFlow,
        invoicesFlow,
        transactionsFlow,
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        selectedInvoiceIndex,
        filters,
    ) { creditCard, invoices, transactions, categories, installments, index, currentFilters ->
        // Invoice owed and its expense/advancePayment/adjustment breakdown, both derived
        // from the ledger (Σ liability-leg entries — task 4.11), not from legacy legs.
        // Read for every invoice's dimension in one grouped query each, not one per invoice.
        val invoiceDimensionIds = invoices.mapNotNull { it.dimensionId }
        // Each answer is per currency; an invoice holds one, by this feature's own
        // guarantee that an invoice's dimension only ever lands on the single LIABILITY
        // account of its card. The reduction is here, beside that guarantee, and never
        // presumed by the ledger (design D8).
        val owedByDimension = entryRepository.owedByDimensionByCurrency(invoiceDimensionIds)
        val flowsByDimension = entryRepository.flowsByDimensionByCurrency(invoiceDimensionIds)
        val owedByInvoiceId = mutableMapOf<Long, Double>()
        val flowsByInvoiceId = mutableMapOf<Long, InvoiceFlows>()
        for (inv in invoices) {
            val dimensionId = inv.dimensionId ?: continue
            owedByInvoiceId[inv.id] = owedByDimension[dimensionId]?.singleOrNull()?.value ?: 0.0
            val flows = flowsByDimension[dimensionId]
            flowsByInvoiceId[inv.id] = InvoiceFlows(
                expense = flows?.expense?.singleOrNull()?.value ?: 0.0,
                advancePayment = flows?.advancePayment?.singleOrNull()?.value ?: 0.0,
                adjustment = flows?.adjustment?.singleOrNull()?.value ?: 0.0,
            )
        }

        // Every figure of this screen is the card's money, so all of them read in the
        // card's own currency (design D17) — asked once, not once per invoice.
        val currency = accountRepository.currencyOf(creditCard)

        val invoice = invoices.getOrNull(index)
        // The rows render archived categories too, so the lookup keeps them — only the
        // filter below offers the open ones.
        val lookup = TransactionFacadeLookup.of(categories, installments)

        val invoiceTransactions = transactions
            .filter { transaction -> transaction.entries.any { it.dimensionId == invoice?.dimensionId } }
        val filteredTransactions = invoiceTransactions
            .filter(currentFilters.category)
            .filter(currentFilters.type, creditCard.accountId)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        // Which emptiness this is comes from the invoice's own transactions, before any
        // filter: an invoice with entries the chips hide is a cut, not an empty invoice.
        val listState = when {
            filteredTransactions.isNotEmpty() -> {
                // Mapped here, through this screen's own perspective — the card — so the
                // screen renders display models and never holds the ledger (design D12).
                InvoiceTransactionsUiState.ListState.Content(
                    filteredTransactions.mapValues { (_, ops) ->
                        ops.mapNotNull {
                            it.toTransactionUi(accountId = creditCard.accountId, lookup = lookup)
                        }
                    }
                )
            }

            invoiceTransactions.isEmpty() -> InvoiceTransactionsUiState.ListState.EmptyInvoice

            else -> InvoiceTransactionsUiState.ListState.EmptyScope(
                canClearFilters = currentFilters.isNotNeutral
            )
        }

        InvoiceTransactionsUiState(
            creditCardName = creditCard.name,
            cardAccountId = creditCard.accountId,
            isArchived = creditCard.isArchived,
            retireAction = retireActionOf(
                entryRepository.hasEntries(creditCard.accountId) ||
                    recurringRepository.hasRecurringForCreditCard(creditCard.id)
            ),
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
                    // The row only renders: spending subtracts from what the card is
                    // worth to the user, an advance payment adds, and an adjustment is
                    // the one line whose direction its label withholds.
                    expense = DisplayAmount.forcedNegative(expense, currency, isApproximate = false),
                    advancePayment = DisplayAmount.forcedPositive(advancePayment, currency, isApproximate = false),
                    adjustment = DisplayAmount.explicitSign(adjustment, currency, isApproximate = false),
                    total = DisplayAmount.natural(
                        owedByInvoiceId.getValue(invoice.id),
                        currency,
                        isApproximate = false,
                    ),
                    dueMonth = invoice.dueMonth,
                    nextDateLabel = nextDateLabel,
                    closingDate = invoice.closingDate,
                    isClosable = invoice.isClosableOn(currentDate),
                    canReopen = invoice.isReopenable(invoices),
                )
            },
            selectedInvoiceIndex = index,
            listState = listState,
            // The filter offers only open categories.
            categories = categories.filterNot { it.isArchived },
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

            // The selected invoice is left alone: it governs the pager and its figures,
            // and an action announced as "clear filters" that changed invoice would do
            // more than it says.
            is InvoiceTransactionsAction.ClearFilters -> {
                filters.value = InvoiceTransactionsFilters(
                    category = null,
                    type = null,
                    recurringOnly = false,
                    installmentOnly = false,
                )
            }

            // Reversible and innocuous (design D8): no confirmation. The screen offers this
            // only for an archived card; the reopened account re-emits and the UI flips back
            // to the active affordances on its own. The card is resolved at action time so no
            // domain model sits in observable state.
            InvoiceTransactionsAction.Unarchive -> {
                val creditCard = creditCardRepository.getCreditCardById(creditCardId) ?: return@launch
                unarchiveCreditCard(creditCard).onLeft { crashlytics.recordException(it) }
            }
        }
    }
}

private data class InvoiceTransactionsFilters(
    val category: Category?,
    val type: TransactionType?,
    val recurringOnly: Boolean,
    val installmentOnly: Boolean,
) {
    /** Whether there is anything for [InvoiceTransactionsAction.ClearFilters] to clear. */
    val isNotNeutral = category != null || type != null || recurringOnly || installmentOnly
}

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { transaction ->
        transaction.nominalDimensionId == category.dimensionId
    }
}

/**
 * The card's own leg is what this screen shows, so the filter reads its direction — a
 * payment credits the card, a purchase debits it.
 *
 * [accountId] is the screen's perspective, and the leg comes from the same definition the
 * rendered item uses. It used to pick the `LIABILITY` leg here by hand while the item was
 * mapped without a perspective, off the account's leg: filtering by income returned a
 * payment that the item beside it then presented as an expense.
 */
private fun List<Transaction>.filter(type: TransactionType?, accountId: Long): List<Transaction> {
    if (type == null) return this
    return filter { transaction ->
        transaction.legUnder(accountId)
            ?.let { deriveTransactionType(it.amount, transaction.entries) } == type
    }
}

private fun List<Transaction>.filter(recurringOnly: Boolean): List<Transaction> {
    if (!recurringOnly) return this
    return filter { transaction -> transaction.recurringId != null }
}

private fun List<Transaction>.filterInstallment(installmentOnly: Boolean): List<Transaction> {
    if (!installmentOnly) return this
    return filter { transaction -> transaction.installmentId != null }
}

/**
 * One invoice's expense/advance-payment/adjustment, already reduced to the single
 * currency its card holds. It is this screen's own shape, not the ledger's: the ledger
 * answers per currency, and the reduction happens here, beside the facade guarantee
 * that makes it valid (design D8).
 */
private data class InvoiceFlows(
    val expense: Double,
    val advancePayment: Double,
    val adjustment: Double,
)
