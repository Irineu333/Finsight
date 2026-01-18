@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finance.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.domain.repository.ITransactionRepository
import com.neoutils.finance.extension.combine
import com.neoutils.finance.extension.toYearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AccountsViewModel(
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val initialAccountId: Long? = null
) : ViewModel() {

    private val selectedAccountIndex = MutableStateFlow(0)

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val filters = MutableStateFlow(
        AccountsFilters(
            category = null,
            type = null,
        )
    )

    private val transactionsFlow = combine(
        accountRepository.observeAllAccounts(),
        selectedAccountIndex,
        selectedMonth,
    ) { accounts, index, month ->
        val account = accounts.getOrNull(index)
        account?.id to month
    }.flatMapLatest { (accountId, month) ->
        if (accountId != null) {
            transactionRepository.observeTransactionsBy(accountId = accountId)
        } else {
            kotlinx.coroutines.flow.flowOf(emptyList())
        }
    }

    val uiState = combine(
        accountRepository.observeAllAccounts(),
        transactionsFlow,
        categoryRepository.observeAllCategories(),
        selectedAccountIndex,
        selectedMonth,
        filters,
    ) { accounts, transactions, categories, index, month, currentFilters ->

        val filteredTransactions = transactions
            .filter { it.date.yearMonth == month }
            .filter(currentFilters.category)
            .filter(currentFilters.type)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        AccountsUiState(
            accounts = accounts.map { account ->
                val allAccountTransactions = transactions.filter { it.account?.id == account.id }
                val initialBalance = allAccountTransactions
                    .filter { it.date.yearMonth < month }
                    .sumOf { it.accountAmount }
                val accountTransactions = allAccountTransactions
                    .filter { it.date.yearMonth <= month }
                val balance = accountTransactions.sumOf { it.accountAmount }
                val monthTransactions = accountTransactions.filter { it.date.yearMonth == month }
                val income = monthTransactions
                    .filter { it.type == Transaction.Type.INCOME }
                    .sumOf { it.amount }
                val expense = monthTransactions
                    .filter { it.type == Transaction.Type.EXPENSE }
                    .sumOf { it.amount }
                val adjustment = monthTransactions
                    .filter { it.type == Transaction.Type.ADJUSTMENT }
                    .sumOf { it.accountAmount }
                val invoicePayment = monthTransactions
                    .filter { it.type == Transaction.Type.INVOICE_PAYMENT }
                    .sumOf { it.amount }
                val advancePayment = monthTransactions
                    .filter { it.type == Transaction.Type.ADVANCE_PAYMENT }
                    .sumOf { it.amount }
                AccountUi(
                    account = account,
                    initialBalance = initialBalance,
                    balance = balance,
                    income = income,
                    expense = expense,
                    adjustment = adjustment,
                    invoicePayment = invoicePayment,
                    advancePayment = advancePayment
                )
            },
            selectedAccountIndex = index,
            selectedMonth = month,
            transactions = filteredTransactions,
            categories = categories,
            selectedCategory = currentFilters.category,
            selectedType = currentFilters.type,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState()
    )

    init {
        initialAccountId?.let {
            setInitialAccount(accountId = it)
        }
    }

    private fun setInitialAccount(
        accountId: Long
    ) = viewModelScope.launch {
        val index = accountRepository
            .getAllAccounts()
            .indexOfFirst { it.id == accountId }

        if (index >= 0) {
            selectedAccountIndex.value = index
        }
    }

    fun onAction(action: AccountsAction) = viewModelScope.launch {
        when (action) {
            is AccountsAction.SelectAccount -> {
                selectedAccountIndex.value = action.index.coerceAtLeast(0)
            }

            is AccountsAction.SelectCategory -> {
                filters.value = filters.value.copy(category = action.category)
            }

            is AccountsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
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
)

private fun List<Transaction>.filter(category: Category?): List<Transaction> {
    if (category == null) return this
    return filter { it.category?.id == category.id }
}

private fun List<Transaction>.filter(type: Transaction.Type?): List<Transaction> {
    if (type == null) return this
    return filter { it.type == type }
}
