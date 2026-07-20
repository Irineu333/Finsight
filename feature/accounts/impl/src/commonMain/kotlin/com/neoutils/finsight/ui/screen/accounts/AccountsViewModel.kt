@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.OperationUi
import com.neoutils.finsight.ui.model.toOperationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AccountsViewModel(
    private val accountRepository: IAccountRepository,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val initialAccountId: Long? = null
) : ViewModel() {

    private val accounts = accountRepository.observeAllAccounts()

    private val selectedAccountId = MutableStateFlow(initialAccountId)

    private val selectedAccountIndex = combine(
        accounts,
        selectedAccountId,
    ) { accounts, selectedAccountId ->
        accounts.indexOfFirst {
            it.id == selectedAccountId
        }.coerceAtLeast(minimumValue = 0)
    }

    private val selectedAccount = combine(
        accounts,
        selectedAccountIndex,
    ) { accounts, index ->
        accounts.getOrNull(index)
    }

    private val operations = operationRepository.observeAllOperations()

    private val operationsUi = combine(
        selectedAccount,
        operations,
    ) { account, operations ->
        // No account selected (e.g. all accounts deleted with the screen open) → no operations.
        account ?: return@combine emptyList()
        // Flat DTO derived from the ledger under this account's perspective; ops
        // whose entries don't touch the account map to null and are omitted.
        operations.mapNotNull { operation -> operation.toOperationUi(accountId = account.id) }
    }

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val accountsWithDomain = combine(
        accounts,
        selectedMonth,
    ) { accounts, month ->
        // Derived entirely from the ledger (task 4.4): opening = Σ entries up to the
        // previous month; balance = Σ entries up to the month; the month's flows come
        // from the per-account aggregate (task 2.4). No summing of legs in memory.
        accounts.map { account ->
            val flows = entryRepository.accountFlows(month = month, accountId = account.id)
            account to AccountUi(
                id = account.id,
                openingBalance = entryRepository.balanceUpTo(target = month.minusMonth(), accountId = account.id),
                balance = entryRepository.balanceUpTo(target = month, accountId = account.id),
                income = flows.income,
                expense = flows.expense,
                adjustment = flows.adjustment,
                invoicePayment = flows.invoicePayment,
            )
        }
    }

    private val filters = MutableStateFlow(
        AccountsFilters(
            category = null,
            type = null,
            recurringOnly = false,
        )
    )

    val uiState = combine(
        accountsWithDomain,
        operationsUi,
        categoryRepository.observeAllCategories(),
        selectedAccountIndex,
        selectedMonth,
        filters,
    ) { accountsPairs, selectedAccountOperations, categories, index, month, currentFilters ->
        val monthOperations = selectedAccountOperations.filter { operation ->
            operation.date.yearMonth == month
        }

        val filteredOperations = monthOperations
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        AccountsUiState.Content(
            accounts = accountsPairs.map { it.second },
            domainAccounts = accountsPairs.map { it.first },
            selectedAccountIndex = index,
            selectedAccountId = accountsPairs.getOrNull(index)?.first?.id,
            selectedMonth = month,
            operations = filteredOperations,
            categories = categories,
            selectedCategory = currentFilters.category,
            selectedType = currentFilters.type,
            showRecurringOnly = currentFilters.recurringOnly,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState.Loading(
            selectedMonth = selectedMonth.value,
        )
    )

    fun onAction(action: AccountsAction) = viewModelScope.launch {
        when (action) {
            is AccountsAction.SelectAccount -> {
                selectedAccountId.value = accountRepository
                    .getAllAccounts()[action.index.coerceAtLeast(0)].id
            }

            is AccountsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is AccountsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is AccountsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            is AccountsAction.SelectMonth -> {
                selectedMonth.value = action.yearMonth
            }

            is AccountsAction.PreviousMonth -> {
                selectedMonth.value = selectedMonth.value.minus(1, DateTimeUnit.MONTH)
            }

            is AccountsAction.NextMonth -> {
                selectedMonth.value = selectedMonth.value.plus(1, DateTimeUnit.MONTH)
            }
        }
    }
}

private data class AccountsFilters(
    val category: Category?,
    val type: TransactionType?,
    val recurringOnly: Boolean,
)

private fun List<OperationUi>.filter(category: Category?): List<OperationUi> {
    if (category == null) return this
    return filter { operation ->
        operation.categoryId == category.id
    }
}

private fun List<OperationUi>.filter(type: TransactionType?): List<OperationUi> {
    if (type == null) return this
    return filter { operation -> operation.direction == type }
}

private fun List<OperationUi>.filter(recurringOnly: Boolean): List<OperationUi> {
    if (!recurringOnly) return this
    return filter { operation -> operation.isRecurring }
}
