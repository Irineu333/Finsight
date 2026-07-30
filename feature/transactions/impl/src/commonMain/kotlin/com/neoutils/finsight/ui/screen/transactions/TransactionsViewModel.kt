@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.transactions

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionLabel
import com.neoutils.finsight.domain.model.TransactionTarget
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import com.neoutils.finsight.ui.screen.transactions.TransactionsUiState.ListState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class TransactionsViewModel(
    private val filterLabel: TransactionLabel?,
    private val category: Category?,
    private val filterTarget: TransactionTarget?,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
    private val entryRepository: IEntryRepository,
    private val consolidateMoney: ConsolidateMoneyUseCase,
) : ViewModel() {

    private val selectedYearMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    /**
     * The screen opens on the whole of the user's money, so the list stays exactly what
     * it was before the scope existed and the summary is what changes to explain it.
     */
    private val selectedScope = MutableStateFlow(TransactionScope.ALL)

    private val filters = MutableStateFlow(
        TransactionsFilters(
            category = category,
            label = filterLabel,
            target = filterTarget,
        )
    )

    val uiState = combine(
        transactionRepository.observeAllTransactions(),
        // The list shows the history of archived categories, so the filter must be
        // able to narrow to one — including closed. This is a filter over existing
        // data, not a selector for a new transaction (which stays open-only).
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
        combine(selectedYearMonth, selectedScope, ::Pair),
        filters
    ) { transactions, categories, installments, (yearMonth, scope), filters ->
        // Every figure comes from the ledger, per scope — never summed over the loaded
        // list (spec `ledger-reporting`). Reactive because observeAllTransactions()
        // re-runs this block on every ledger write, and on scope or month change.
        // Every line of it spans accounts, so every line is a consolidated figure: the
        // reducer is what denominates them, and the base currency is never named here
        // (design D29).
        val balanceOverview = entryRepository.balanceOverview(
            scope = scope,
            month = yearMonth,
            consolidate = consolidateMoney,
        )

        // The scope decides between account and card; offering the chip as well would
        // be the same decision twice, able to contradict itself.
        val target = filters.target.takeIf { scope == TransactionScope.ALL }

        // Same rule for instalments, which only exist on a card: a filter the scope no
        // longer offers must stop narrowing too, or it would go on cutting invisibly.
        val installmentOnly = filters.installmentOnly && scope != TransactionScope.ACCOUNTS

        // The list still shows a category icon and an installment badge; the ledger only
        // hands out the identities behind them (design D6).
        val lookup = TransactionFacadeLookup.of(categories, installments)

        val visible = transactions
            .filter(filters.recurringOnly)
            .filterInstallment(installmentOnly)
            .filter(filters.category)
            .filter(filters.label)
            .filter(target)
            // The scope narrows the list to the transactions touching its perimeter,
            // so summary and list always answer for the same set of accounts.
            .filter { scope.contains(it) }
            .filter { it.date.yearMonth == yearMonth }
            .sortedByDescending { it.date }
            .groupBy { it.date }

        TransactionsUiState(
            balanceOverview = balanceOverview,
            selectedScope = scope,
            selectedYearMonth = yearMonth,
            categories = categories,
            selectedCategory = filters.category,
            selectedLabel = filters.label,
            selectedTarget = target,
            showRecurringOnly = filters.recurringOnly,
            showInstallmentOnly = installmentOnly,
            // Which emptiness this is comes from the list *before* any filter, never
            // from which controls are active: with every filter neutral, a month with
            // nothing in it is still a cut — as long as some other month has something.
            listState = when {
                // Mapped here, not in the composable: the screen renders display models
                // and never holds the ledger (`presentation-mapping`, design D12). This
                // list declares no perspective — it spans every account and card.
                visible.isNotEmpty() -> ListState.Content(
                    visible.mapValues { (_, ops) ->
                        ops.mapNotNull { it.toTransactionUi(lookup = lookup) }
                    }
                )
                transactions.isEmpty() -> ListState.EmptyLedger
                else -> ListState.EmptyScope(
                    // The effective filters, not the stored ones: a filter the scope has
                    // already neutralised is absent from the row and narrows nothing, so
                    // clearing it would change neither the chips nor the list.
                    canClearFilters = filters.category != null ||
                        filters.label != null ||
                        target != null ||
                        filters.recurringOnly ||
                        installmentOnly,
                )
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = TransactionsUiState()
    )

    fun onAction(action: TransactionsAction) = viewModelScope.launch {
        when (action) {
            is TransactionsAction.SelectMonth -> {
                selectedYearMonth.value = action.yearMonth
            }

            is TransactionsAction.SelectScope -> {
                selectedScope.value = action.scope
            }

            is TransactionsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is TransactionsAction.SelectLabel -> {
                filters.value = filters.value.copy(label = action.label)
            }

            is TransactionsAction.SelectTarget -> {
                filters.value = filters.value.copy(target = action.target)
            }

            is TransactionsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            is TransactionsAction.ToggleInstallment -> {
                filters.value = filters.value.copy(installmentOnly = action.enabled)
            }

            // Month and scope survive: they govern the summary as well, and clearing a
            // filter must not rewrite figures nobody asked to change. The filters that
            // arrived by route go with the rest — on this screen they are chips like any
            // other, and keeping them would make the action inexplicable.
            is TransactionsAction.ClearFilters -> {
                filters.value = TransactionsFilters()
            }
        }
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

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.nominalDimensionId == category.dimensionId }
}

private fun List<Transaction>.filter(label: TransactionLabel?): List<Transaction> {
    if (label == null) return this
    return filter { transaction -> transaction.label == label }
}

private fun List<Transaction>.filter(target: TransactionTarget?): List<Transaction> {
    if (target == null) return this
    return filter { transaction -> transaction.hasLiabilityLeg == target.isCreditCard }
}
