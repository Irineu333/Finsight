@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetRecurringMonthOverviewUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.currentYearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RecurringViewModel(
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val getRecurringMonthOverview: GetRecurringMonthOverviewUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
    observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(RecurringFilter.ACTIVE)

    /**
     * The month of the summary card, and of nothing else. It opens on the current one
     * and the list never sees it: a template has no month, only its occurrence has.
     */
    private val selectedYearMonth = MutableStateFlow(clock.currentYearMonth())

    val uiState = combine(
        recurringRepository.observeAllRecurring(),
        occurrenceRepository.observeAllOccurrences(),
        filter,
        selectedYearMonth,
        // Not optional. The fact half of the summary is a `suspend` read, so nothing
        // about it re-runs on its own: without a trigger the figures would keep whatever
        // value they had when the screen opened while the ledger moved underneath them.
        // The consolidation signal rather than the ledger's alone, because a rate
        // registered in settings writes no entry and would move these figures too.
        observeConsolidationChanges(),
    ) { recurring, occurrences, filter, month, _ ->
        // The big CTA is for a genuinely empty database only; a filter that merely has
        // nothing to show is Content with an empty list.
        if (recurring.isEmpty()) {
            RecurringUiState.Empty(filter = filter)
        } else {
            // Resolved **once per emission**, and shared by the list and the summary. It
            // used to be asked item by item inside this block, which for a card template
            // is a query each; summing the month over the same structure would have
            // doubled it.
            val currencies = currenciesOf(recurring)

            RecurringUiState.Content(
                // The row renders a figure, not a bare number: each amount is denominated
                // by the account the template names (design D17), because only this layer
                // can resolve a card to its account. A template whose account is gone
                // states no currency, and its row says so in the clear.
                filteredRecurring = filteredFor(filter, recurring).map { item ->
                    RecurringItem(
                        recurring = item,
                        amount = currencies[item.id]?.let { currency ->
                            DisplayAmount.magnitude(
                                value = item.amount,
                                currency = currency,
                                isApproximate = false,
                            )
                        },
                    )
                },
                filter = filter,
                selectedYearMonth = month,
                summary = getRecurringMonthOverview(
                    recurringList = recurring,
                    occurrences = occurrences,
                    month = month,
                    currencyOf = { currencies[it.id] },
                ).toSummary(month = month, consolidate = consolidateMoney),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState.Loading(),
    )

    /**
     * What denominates each template, by id. A template with no resolvable source is
     * absent from the map rather than mapped to `null`: absence is what both consumers
     * already read as "there is no currency for this one".
     */
    private suspend fun currenciesOf(recurring: List<Recurring>): Map<Long, String> =
        recurring.mapNotNull { item ->
            accountRepository.currencyOf(item)?.let { item.id to it }
        }.toMap()

    private fun filteredFor(
        filter: RecurringFilter,
        recurring: List<Recurring>,
    ): List<Recurring> {
        val active = recurring.filterNot { it.isArchived }
        return when (filter) {
            // Deliberately not sectioned by type: the useful ordering here is the one
            // that already exists, and an archived recurring leaves the view entirely
            // instead of sinking to the bottom (design D6).
            RecurringFilter.ACTIVE -> active
            RecurringFilter.EXPENSE -> active.filter { it.type == TransactionType.EXPENSE }
            RecurringFilter.INCOME -> active.filter { it.type == TransactionType.INCOME }
            RecurringFilter.ARCHIVED -> recurring.filter { it.isArchived }
        }.sortedBy { it.createdAt }
    }

    fun onAction(action: RecurringAction) {
        when (action) {
            is RecurringAction.SelectFilter -> filter.value = action.filter
            is RecurringAction.SelectMonth -> selectedYearMonth.value = action.yearMonth
        }
    }
}
