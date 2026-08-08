package com.neoutils.finsight.ui.screen.recurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.extension.currencyOf
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RecurringViewModel(
    private val recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(RecurringFilter.ACTIVE)

    val uiState = combine(
        recurringRepository.observeAllRecurring(),
        filter,
    ) { recurring, filter ->
        // The big CTA is for a genuinely empty database only; a filter that merely has
        // nothing to show is Content with an empty list.
        if (recurring.isEmpty()) {
            RecurringUiState.Empty(filter = filter)
        } else {
            RecurringUiState.Content(
                // The card renders a figure, not a bare number: each amount is
                // denominated here, by the account the template names (design D17),
                // because only this layer can resolve a card to its account. A template
                // whose account is gone states no currency, and its card shows no figure.
                filteredRecurring = filteredFor(filter, recurring).map { item ->
                    RecurringItem(
                        recurring = item,
                        amount = accountRepository.currencyOf(item)?.let { currency ->
                            DisplayAmount.magnitude(
                                value = item.amount,
                                currency = currency,
                                isApproximate = false,
                            )
                        },
                    )
                },
                filter = filter,
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState.Loading(),
    )

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
        }
    }
}
