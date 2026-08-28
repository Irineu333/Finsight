package com.neoutils.finsight.ui.screen.archivedRecurring

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.extension.currencyBy
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The archived recurrings, as a flat list.
 *
 * There is no month here and no partition: an archived template generates no cycle in any
 * month, so it has no state of cycle to be grouped by and no month to be summarised. What
 * this destination owes is that archiving stays reversible — it is the only path to
 * un-archiving — and a list is all that takes.
 */
class ArchivedRecurringViewModel(
    recurringRepository: IRecurringRepository,
    private val accountRepository: IAccountRepository,
) : ViewModel() {

    val uiState = recurringRepository.observeAllRecurring()
        .map { recurring -> recurring.filter(Recurring::isArchived) }
        .map { archived ->
            if (archived.isEmpty()) {
                ArchivedRecurringUiState.Empty
            } else {
                // The whole chart in one read, as the monthly list does: a card template
                // is denominated by the `LIABILITY` account the card projects onto, which
                // the account facade does not list.
                val currencyByAccountId = accountRepository.getAllLedgerAccounts()
                    .associate { it.id to it.currency }

                ArchivedRecurringUiState.Content(
                    archived.sortedBy { it.createdAt }.map { item ->
                        ArchivedRecurringUi(
                            recurring = item,
                            amount = item
                                .currencyBy { currencyByAccountId[it.accountId] }
                                ?.let { currency ->
                                    DisplayAmount.magnitude(
                                        value = item.amount,
                                        currency = currency,
                                        isApproximate = false,
                                    )
                                },
                        )
                    }
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ArchivedRecurringUiState.Loading,
        )
}
