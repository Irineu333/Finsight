@file:OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)

package com.neoutils.finsight.ui.screen.creditCards

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.extension.deriveTransactionType
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.matches
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.extension.requireCurrencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.domain.usecase.Limit
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.ui.mapper.InvoiceUiMapper
import com.neoutils.finsight.ui.model.CreditCardUi
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.time.ExperimentalTime

class CreditCardsViewModel(
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
    private val creditCardRepository: ICreditCardRepository,
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val invoiceUiMapper: InvoiceUiMapper,
    private val calculateAvailableLimit: CalculateAvailableLimitUseCase,
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
            subject = null,
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
            transactionRepository.observeTransactionsBy(dimensionId = invoice.dimensionId)
        } else {
            flowOf(emptyList())
        }
    }

    val uiState = combine(
        creditCards,
        transactionsFlow,
        invoicesFlow,
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        selectedCardIndex,
        filters,
    ) { creditCards, transactions, invoices, categories, installments, index, currentFilters ->
        if (creditCards.isEmpty()) {
            return@combine CreditCardsUiState.Empty
        }

        // This screen shows one card at a time, so it reads through that card's own leg.
        val perspectiveAccountId = creditCards.getOrNull(index)?.accountId
        // The rows render archived categories too, so the lookup keeps them.
        val lookup = TransactionFacadeLookup.of(categories, installments)

        // Everything the other controls leave standing — the universe the axis cuts, and
        // the one the unclassified value is offered against.
        val cuttable = transactions
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .filterInstallment(currentFilters.installmentOnly)

        val filteredTransactions = cuttable
            .filter(currentFilters.subject)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        // Which emptiness this is comes from the invoice's own transactions, before any
        // filter: an invoice with entries the chips hide is a cut, not an empty invoice.
        val listState = when {
            filteredTransactions.isNotEmpty() -> {
                // Mapped here rather than in the composable (design D12), which is also
                // what keeps the perspective from having to be remembered by the screen.
                CreditCardsUiState.ListState.Content(
                    filteredTransactions.mapValues { (_, ops) ->
                        ops.mapNotNull {
                            it.toTransactionUi(accountId = perspectiveAccountId, lookup = lookup)
                        }
                    }
                )
            }

            transactions.isEmpty() -> CreditCardsUiState.ListState.EmptyInvoice

            else -> CreditCardsUiState.ListState.EmptyScope(
                canClearFilters = currentFilters.isNotNeutral
            )
        }

        // One read for the whole pager, not one per card: asking card by card inside
        // the loop is what made this screen cost N invoice queries plus N ledger
        // reads (design D7).
        val limits = calculateAvailableLimit(creditCards.map { it.id })

        val cards = creditCards.map { creditCard ->
            val cardInvoices = invoices[creditCard.id].orEmpty()
            val invoice = cardInvoices.currentUnpaid()
            val currency = accountRepository.requireCurrencyOf(creditCard)
            val ui = CreditCardUi(
                cardId = creditCard.id,
                iconKey = creditCard.iconKey,
                name = creditCard.name,
                closingDay = creditCard.closingDay,
                dueDay = creditCard.dueDay,
                limit = creditCard.limit,
                invoiceUi = invoice?.let {
                    invoiceUiMapper.toUi(
                        invoice = it,
                        cardInvoices = cardInvoices,
                        limit = limits[creditCard.id] ?: Limit.NONE,
                    )
                },
                mustPreserve = entryRepository.hasEntries(creditCard.accountId) ||
                    recurringRepository.hasRecurringForCreditCard(creditCard.id),
            )
            CardRow(
                domain = creditCard,
                invoice = invoice,
                ui = ui,
                limit = DisplayAmount.magnitude(
                    creditCard.limit,
                    currency,
                    isApproximate = false,
                ),
            )
        }

        CreditCardsUiState.Content(
            creditCards = cards.map { it.ui },
            domainCards = cards.map { it.domain },
            domainInvoices = cards.map { it.invoice },
            cardLimits = cards.map { it.limit },
            selectedCardIndex = index,
            listState = listState,
            // The filter offers only open categories.
            categories = categories.filterNot { it.isArchived },
            selectedSubject = currentFilters.subject,
            hasUncategorized = cuttable.any { it.matches(SpendingSubject.Uncategorized) },
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

            is CreditCardsAction.SelectSubject -> {
                filters.value = filters.value.copy(subject = action.subject)
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

            // The selected card is left alone: it governs the pager and its figures, and
            // an action announced as "clear filters" that changed cards would do more
            // than it says.
            is CreditCardsAction.ClearFilters -> {
                filters.value = CreditCardsFilters(
                    subject = null,
                    type = null,
                    recurringOnly = false,
                    installmentOnly = false,
                )
            }
        }
    }
}

/** A card and everything the screen needs beside its flat [ui] — see [CreditCardsUiState.Content]. */
private data class CardRow(
    val domain: com.neoutils.finsight.domain.model.CreditCard,
    val invoice: Invoice?,
    val ui: CreditCardUi,
    val limit: DisplayAmount,
)

private data class CreditCardsFilters(
    val subject: SpendingSubject?,
    val type: TransactionType?,
    val recurringOnly: Boolean,
    val installmentOnly: Boolean,
) {
    /** Whether there is anything for [CreditCardsAction.ClearFilters] to clear. */
    val isNotNeutral = subject != null || type != null || recurringOnly || installmentOnly
}

/**
 * The cut by the analytic axis. What each of its values contains is decided by
 * `Transaction.matches` in `core/model` — in particular, "unclassified" is not
 * `nominalDimensionId == null`, which is also true of what has no nominal leg at all.
 */
private fun List<Transaction>.filter(subject: SpendingSubject?): List<Transaction> {
    if (subject == null) return this
    return filter { it.matches(subject) }
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
    return filter { transaction -> transaction.recurringId != null }
}

private fun List<Transaction>.filterInstallment(installmentOnly: Boolean): List<Transaction> {
    if (!installmentOnly) return this
    return filter { transaction -> transaction.installmentId != null }
}
