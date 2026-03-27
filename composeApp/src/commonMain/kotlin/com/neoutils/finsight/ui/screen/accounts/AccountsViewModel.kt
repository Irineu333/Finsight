@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.signedImpact
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.AccountUi
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

    private val selectedAccountId = MutableStateFlow(initialAccountId)

    private val selectedAccountIndex = combine(
        accountRepository.observeAllAccounts(),
        selectedAccountId,
    ) { accounts, selectedAccountId ->
        accounts.indexOfFirst {
            it.id == selectedAccountId
        }.coerceAtLeast(minimumValue = 0)
    }

    private val selectedAccount = combine(
        accountRepository.observeAllAccounts(),
        selectedAccountIndex,
    ) { accounts, index ->
        accounts.getOrNull(index) ?: accounts.first()
    }

    private val operationsBySelectedAccount = selectedAccount.flatMapLatest { account ->
        operationRepository.observeAllOperations().map { operations ->
            operations.filter { operation ->
                operation.transactions.any { transaction ->
                    transaction.account?.id == account.id
                }
            }.map { operation ->
                val selectedTransactions = operation.transactions.filter { transaction ->
                    transaction.account?.id == account.id
                }
                if (selectedTransactions.isEmpty()) {
                    operation
                } else {
                    operation.copy(
                        transactions = selectedTransactions + operation.transactions.filterNot { transaction ->
                            transaction.account?.id == account.id
                        }
                    )
                }
            }
        }
    }

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        AccountsFilters(
            category = null,
            type = null,
            recurringOnly = false,
        )
    )

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        operationsBySelectedAccount,
        categoryRepository.observeAllCategories(),
        selectedAccountIndex,
        selectedMonth,
        filters,
    ) { accounts, operations, categories, index, month, currentFilters ->
        val transactions = operations.flatMap { it.transactions }

        val monthOperations = operations.filter { it.date.yearMonth == month }

        val filteredOperations = monthOperations
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        AccountsUiState.Content(
            accounts = accounts.map { account ->
                val allAccountTransactions = transactions.filter { it.account?.id == account.id }

                val initialBalance = allAccountTransactions
                    .filter { it.date.yearMonth < month }
                    .sumOf { it.signedImpact() }

                val accountTransactions = allAccountTransactions
                    .filter { it.date.yearMonth <= month }

                val balance = accountTransactions.sumOf { it.signedImpact() }

                val monthTransactions = accountTransactions.filter { it.date.yearMonth == month }

                val income = monthTransactions
                    .filter { it.type == Transaction.Type.INCOME }
                    .sumOf { it.amount }

                val expense = monthTransactions
                    .filter { it.type == Transaction.Type.EXPENSE && !it.isInvoicePayment }
                    .sumOf { it.amount }

                val adjustment = monthTransactions
                    .filter { it.type == Transaction.Type.ADJUSTMENT }
                    .sumOf { it.signedImpact() }

                val invoicePayment = monthTransactions
                    .filter { it.type == Transaction.Type.EXPENSE && it.isInvoicePayment }
                    .sumOf { it.amount }

                AccountUi(
                    account = account,
                    initialBalance = initialBalance,
                    balance = balance,
                    income = income,
                    expense = expense,
                    adjustment = adjustment,
                    invoicePayment = invoicePayment,
                    advancePayment = 0.0
                )
            },
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

private fun List<Operation>.filter(category: Category?): List<Operation> {
    if (category == null) return this
    return filter { operation ->
        operation.category?.id == category.id || operation.primaryTransaction.category?.id == category.id
    }
}

private fun List<Operation>.filter(type: Transaction.Type?): List<Operation> {
    if (type == null) return this
    return filter { operation -> operation.type == type }
}

private fun List<Operation>.filter(recurringOnly: Boolean): List<Operation> {
    if (!recurringOnly) return this
    return filter { operation -> operation.recurring != null }
}
