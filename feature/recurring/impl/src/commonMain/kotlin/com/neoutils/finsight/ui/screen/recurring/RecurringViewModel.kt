@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.extension.currencyBy
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringCycle
import com.neoutils.finsight.domain.model.RecurringCycleStatus
import com.neoutils.finsight.domain.model.RecurringCycles
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.domain.usecase.GetRecurringCyclesUseCase
import com.neoutils.finsight.domain.usecase.GetRecurringMonthOverviewUseCase
import com.neoutils.finsight.domain.usecase.ObserveConsolidationChangesUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.currentYearMonth
import com.neoutils.finsight.extension.today
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.toTransactionUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class RecurringViewModel(
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
    private val categoryRepository: ICategoryRepository,
    private val transactionRepository: ITransactionRepository,
    private val occurrenceRepository: IRecurringOccurrenceRepository,
    private val getRecurringCycles: GetRecurringCyclesUseCase,
    private val getRecurringMonthOverview: GetRecurringMonthOverviewUseCase,
    private val consolidateMoney: ConsolidateMoneyUseCase,
    observeConsolidationChanges: ObserveConsolidationChangesUseCase,
    private val clock: Clock,
) : ViewModel() {

    private val filter = MutableStateFlow(RecurringFilter.ALL)

    /**
     * The month of the whole screen. It opens on the current one, and what the list
     * shows are the cycles of it — the summary and the list answer for the same month.
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
        // The big CTA is for a genuinely empty database only; a month that merely has no
        // cycle is Content with no section.
        if (recurring.isEmpty()) {
            RecurringUiState.Empty(filter = filter)
        } else {
            // One resolution per emission, shared by the list and the summary: two
            // consumers of the same question, and neither pays for it twice.
            val currencies = currenciesOf(recurring)

            // The partition, asked once and read twice — by the sections and by the
            // forecast. Asking for it a second time inside the overview would be a
            // second walk over the same month, free to answer differently.
            val cycles = getRecurringCycles(
                recurringList = recurring,
                occurrences = occurrences,
                month = month,
                today = clock.today(),
            )

            RecurringUiState.Content(
                sections = sectionsOf(cycles, filter, currencies),
                filter = filter,
                selectedYearMonth = month,
                // The whole partition, never the filtered one: the cut by nature governs
                // the list and must not move a figure of the card.
                summary = getRecurringMonthOverview(
                    cycles = cycles,
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
     * The sections, in the order the states are declared in and with no empty one.
     *
     * The cut by nature is applied before the sections are built, so it narrows each of
     * them without changing how the list is organised — a section that the cut emptied
     * is not rendered, exactly like one the month left empty.
     */
    private suspend fun sectionsOf(
        cycles: RecurringCycles,
        filter: RecurringFilter,
        currencies: Map<Long, String>,
    ): List<RecurringSection> {
        val kept = RecurringCycleStatus.entries.associateWith { status ->
            cycles[status].filter { filter.accepts(it.recurring) }
        }

        val posted = ledgerRowsOf(kept.getValue(RecurringCycleStatus.POSTED))

        return kept.mapNotNull { (status, cyclesOfStatus) ->
            if (cyclesOfStatus.isEmpty()) return@mapNotNull null

            RecurringSection(
                status = status,
                cycles = cyclesOfStatus.map { cycle ->
                    posted[cycle.recurring.id] ?: RecurringCycleUi.Template(
                        recurring = cycle.recurring,
                        // The row renders a figure, not a bare number: each amount is
                        // denominated by the account the template names (design D17),
                        // because only this layer can resolve a card to its account. A
                        // template whose account is gone states no currency, and its row
                        // says so in the clear.
                        amount = currencies[cycle.recurring.id]?.let { currency ->
                            DisplayAmount.magnitude(
                                value = cycle.recurring.amount,
                                currency = currency,
                                isApproximate = false,
                            )
                        },
                    )
                },
            )
        }
    }

    /**
     * What the posted cycles actually registered, by template id — **one query for the
     * whole section**.
     *
     * The ledger is asked for the transactions the occurrences point at, all of them at
     * once: asked row by row this would be one read per posted cycle, on every emission
     * of a `combine` with five sources.
     *
     * The path is the occurrence's foreign key, never `transactions.recurringId`: that
     * column is grouping metadata and no read of the ledger consults it.
     *
     * A cycle whose transaction cannot be read is **absent from the map**, and the caller
     * falls back to the template row. The foreign key is `CASCADE`, so deleting the
     * transaction deletes the occurrence and the cycle goes back to being pending on its
     * own — this is the net behind that, and it drops a row from no section.
     */
    private suspend fun ledgerRowsOf(
        posted: List<RecurringCycle>,
    ): Map<Long, RecurringCycleUi.Posted> {
        if (posted.isEmpty()) return emptyMap()

        val transactionIdByRecurring = posted.mapNotNull { cycle ->
            cycle.occurrence?.transactionId?.let { cycle.recurring.id to it }
        }.toMap()

        val transactions = transactionRepository
            .getTransactionsByIds(transactionIdByRecurring.values.toSet())
            .associateBy { it.id }

        // Closed ones included: a category taken out of circulation still labels the
        // history it classified, and this section is history.
        val lookup = TransactionFacadeLookup.of(
            categories = categoryRepository.getAllCategoriesIncludingClosed(),
        )

        return posted.mapNotNull { cycle ->
            val transaction = transactionIdByRecurring[cycle.recurring.id]
                ?.let { transactions[it] }
                ?: return@mapNotNull null
            val ui = transaction.toTransactionUi(lookup = lookup) ?: return@mapNotNull null

            cycle.recurring.id to RecurringCycleUi.Posted(
                recurring = cycle.recurring,
                transaction = ui,
            )
        }.toMap()
    }

    /**
     * What denominates each template, by id — **one query for the whole list**.
     *
     * The chart of accounts is read once and a card's account becomes a lookup in it,
     * instead of a query per template. Asked row by row, opening the screen cost one
     * `getAccountById` for every card template, and paid it again on every ledger write,
     * every filter change and every month change — the `combine` has five sources and
     * any of them redoes this walk.
     *
     * The whole chart, not the account facade: the account a card projects onto is a
     * `LIABILITY` row, and the facade lists `ASSET` only.
     *
     * A template with no resolvable source is absent from the map rather than mapped to
     * `null`: absence is what both consumers already read as "there is no currency for
     * this one".
     */
    private suspend fun currenciesOf(recurring: List<Recurring>): Map<Long, String> {
        val currencyByAccountId = accountRepository.getAllLedgerAccounts()
            .associate { it.id to it.currency }

        return recurring.mapNotNull { item ->
            item.currencyBy { currencyByAccountId[it.accountId] }?.let { item.id to it }
        }.toMap()
    }

    fun onAction(action: RecurringAction) {
        when (action) {
            is RecurringAction.SelectFilter -> filter.value = action.filter
            is RecurringAction.SelectMonth -> selectedYearMonth.value = action.yearMonth
        }
    }
}

/** The cut by nature, transversal to the sections: it narrows each, and reorders none. */
private fun RecurringFilter.accepts(recurring: Recurring): Boolean = when (this) {
    RecurringFilter.ALL -> true
    RecurringFilter.EXPENSE -> recurring.type == TransactionType.EXPENSE
    RecurringFilter.INCOME -> recurring.type == TransactionType.INCOME
}
