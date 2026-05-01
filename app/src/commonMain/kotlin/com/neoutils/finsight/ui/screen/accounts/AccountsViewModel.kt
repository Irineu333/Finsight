@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.OperationPerspective
import com.neoutils.finsight.ui.model.OperationUi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AccountsViewModel(
    private val accountRepository: IAccountRepository,
    private val operationRepository: IOperationRepository,
    private val categoryRepository: ICategoryRepository,
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
        accounts.getOrNull(index) ?: accounts.first()
    }

    private val operations = operationRepository.observeAllOperations()

    private val operationsUi = combine(
        selectedAccount,
        operations,
    ) { account, operations ->
        val perspective = OperationPerspective.Account(accountId = account.id)
        operations.mapNotNull { operation ->
            perspective.resolve(
                operation = operation,
            )?.let {
                OperationUi(
                    operation = operation,
                    perspective = perspective,
                )
            }
        }
    }

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val accountsUi = combine(
        accounts,
        operations,
        selectedMonth,
    ) { accounts, operations, month ->
        val allTransactions = operations.flatMap { operation ->
            operation.transactions
        }

        accounts.map { account ->
            val transactions = allTransactions.filter { transaction ->
                transaction.account?.id == account.id
            }

            AccountUi(
                account = account,
                transactions = transactions,
                month = month,
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
        accountsUi,
        operationsUi,
        categoryRepository.observeAllCategories(),
        selectedAccountIndex,
        selectedMonth,
        filters,
    ) { accountsUi, selectedAccountOperations, categories, index, month, currentFilters ->
        val monthOperations = selectedAccountOperations.filter { operation ->
            operation.displayDate.yearMonth == month
        }

        val filteredOperations = monthOperations
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .sortedByDescending { it.displayDate }
            .groupBy { it.displayDate }

        AccountsUiState.Content(
            accounts = accountsUi,
            selectedAccountIndex = index,
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
    val type: Transaction.Type?,
    val recurringOnly: Boolean,
)

private fun List<OperationUi>.filter(category: Category?): List<OperationUi> {
    if (category == null) return this
    return filter { operation ->
        operation.displayCategory?.id == category.id
    }
}

private fun List<OperationUi>.filter(type: Transaction.Type?): List<OperationUi> {
    if (type == null) return this
    return filter { operation -> operation.displayType == type }
}

private fun List<OperationUi>.filter(recurringOnly: Boolean): List<OperationUi> {
    if (!recurringOnly) return this
    return filter { operation -> operation.recurring != null }
}
