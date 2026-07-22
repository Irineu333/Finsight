@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.toYearMonth
import com.neoutils.finsight.ui.model.AccountUi
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.ui.model.toTransactionUi
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
    private val transactionRepository: ITransactionRepository,
    private val categoryRepository: ICategoryRepository,
    private val installmentRepository: IInstallmentRepository,
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

    private val transactions = transactionRepository.observeAllTransactions()

    private val transactionsUi = combine(
        selectedAccount,
        transactions,
        // The row still shows a category icon and an installment badge; the ledger
        // hands out only the identities behind them (design D6).
        categoryRepository.observeAllCategoriesIncludingClosed(),
        installmentRepository.observeAllInstallments(),
    ) { account, transactions, categories, installments ->
        // No account selected (e.g. all accounts deleted with the screen open) → no transactions.
        account ?: return@combine emptyList()
        val lookup = TransactionFacadeLookup.of(categories, installments)
        // Flat DTO derived from the ledger under this account's perspective; ops
        // whose entries don't touch the account map to null and are omitted.
        transactions.mapNotNull { transaction ->
            transaction.toTransactionUi(accountId = account.id, lookup = lookup)
        }
    }

    private val selectedMonth = MutableStateFlow(Clock.System.now().toYearMonth())

    private val accountsWithDomain = combine(
        accounts,
        selectedMonth,
        // The figures below are SQL aggregates, not flows: without a ledger signal
        // this only recomputed when the account list or the month changed, so a
        // balance adjustment left the cards showing the old number.
        entryRepository.observeLedgerChanges(),
    ) { accounts, month, _ ->
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
                hasMovement = entryRepository.hasEntries(account.id),
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
        transactionsUi,
        categoryRepository.observeAllCategories(),
        selectedAccountIndex,
        selectedMonth,
        filters,
    ) { accountsPairs, selectedAccountTransactions, categories, index, month, currentFilters ->
        val monthTransactions = selectedAccountTransactions.filter { transaction ->
            transaction.date.yearMonth == month
        }

        val filteredTransactions = monthTransactions
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
            transactions = filteredTransactions,
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

private fun List<TransactionUi>.filter(category: Category?): List<TransactionUi> {
    if (category == null) return this
    return filter { transaction ->
        transaction.categoryId == category.id
    }
}

private fun List<TransactionUi>.filter(type: TransactionType?): List<TransactionUi> {
    if (type == null) return this
    return filter { transaction -> transaction.direction == type }
}

private fun List<TransactionUi>.filter(recurringOnly: Boolean): List<TransactionUi> {
    if (!recurringOnly) return this
    return filter { transaction -> transaction.isRecurring }
}
