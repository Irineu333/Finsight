package com.neoutils.finsight.feature.recurring.screen

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.accounts.repository.IAccountRepository
import com.neoutils.finsight.feature.categories.repository.ICategoryRepository
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class RecurringViewModel(
    recurringRepository: IRecurringRepository,
    accountRepository: IAccountRepository,
    categoryRepository: ICategoryRepository,
    creditCardRepository: ICreditCardRepository,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(RecurringFilter.ALL)
    private val selectedStatusFilter = MutableStateFlow(RecurringStatusFilter.ACTIVE)

    val uiState = combine(
        recurringRepository.observeAllRecurring(),
        accountRepository.observeAllAccounts(),
        categoryRepository.observeAllCategories(),
        creditCardRepository.observeAllCreditCards(),
        selectedFilter,
        selectedStatusFilter,
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val recurring = args[0] as List<Recurring>
        val accounts = args[1] as List<com.neoutils.finsight.feature.accounts.model.Account>
        val categories = args[2] as List<com.neoutils.finsight.feature.categories.model.Category>
        val creditCards = args[3] as List<com.neoutils.finsight.feature.creditCards.model.CreditCard>
        val filter = args[4] as RecurringFilter
        val statusFilter = args[5] as RecurringStatusFilter

        val filteredRecurring = recurring
            .filter { r ->
                when (filter) {
                    RecurringFilter.ALL -> true
                    RecurringFilter.INCOME -> r.type == Recurring.Type.INCOME
                    RecurringFilter.EXPENSE -> r.type == Recurring.Type.EXPENSE
                }
            }
            .filter { r ->
                when (statusFilter) {
                    RecurringStatusFilter.ACTIVE -> r.isActive
                    RecurringStatusFilter.INACTIVE -> !r.isActive
                    RecurringStatusFilter.ALL -> true
                }
            }
            .sortedWith(compareByDescending<Recurring> { it.isActive }.thenBy { it.createdAt })

        if (filteredRecurring.isEmpty()) {
            RecurringUiState.Empty(selectedFilter = filter, selectedStatusFilter = statusFilter)
        } else {
            RecurringUiState.Content(
                filteredRecurring = filteredRecurring,
                selectedFilter = filter,
                selectedStatusFilter = statusFilter,
                accountsById = accounts.associateBy { it.id },
                categoriesById = categories.associateBy { it.id },
                creditCardsById = creditCards.associateBy { it.id },
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = RecurringUiState.Loading(),
    )

    fun onAction(action: RecurringAction) {
        when (action) {
            is RecurringAction.SelectFilter -> selectedFilter.value = action.filter
            is RecurringAction.SelectStatusFilter -> selectedStatusFilter.value = action.filter
        }
    }
}
