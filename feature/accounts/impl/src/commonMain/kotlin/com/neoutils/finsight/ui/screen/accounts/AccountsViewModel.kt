@file:OptIn(ExperimentalCoroutinesApi::class, ExperimentalTime::class)

package com.neoutils.finsight.ui.screen.accounts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SpendingSubject
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.combine
import com.neoutils.finsight.extension.today
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
    private val clock: Clock,
    private val initialAccountId: Long? = null
) : ViewModel() {

    private val today = clock.today()

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

    private val selectedMonth = MutableStateFlow(today.yearMonth)

    private val accountsWithDomain = combine(
        accounts,
        selectedMonth,
        // The figures below are SQL aggregates, not flows: without a ledger signal
        // this only recomputed when the account list or the month changed, so a
        // balance adjustment left the cards showing the old number.
        entryRepository.observeLedgerChanges(),
    ) { accounts, month, _ ->
        // Resolving the facade into a dimension is this feature's job, not the
        // ledger's: it takes the identity and never learns what it names (design D3).
        val yieldDimensionId = categoryRepository
            .getCategoryBySystemKey(SystemCategoryKey.YIELD)
            ?.dimensionId
        // Derived entirely from the ledger (task 4.4): opening = Σ entries up to the
        // previous month; balance = Σ entries up to the month; the month's flows come
        // from the per-account aggregate (task 2.4). No summing of legs in memory.
        accounts.map { account ->
            val flows = entryRepository.accountFlows(
                month = month,
                accountId = account.id,
                yieldDimensionId = yieldDimensionId,
            )
            account to AccountUi(
                id = account.id,
                // The card only renders: the sign of each line is the effect of that
                // figure on the account's balance, and it is decided here, once.
                // Every figure on this card belongs to one account, so it is denominated
                // by that account and never by the base currency (design D29), and it is
                // exact because nothing was converted to get it.
                openingBalance = DisplayAmount.natural(
                    entryRepository.accountBalanceUpTo(accountId = account.id, target = month.minusMonth()),
                    account.currency,
                    isApproximate = false,
                ),
                balance = DisplayAmount.natural(
                    entryRepository.accountBalanceUpTo(accountId = account.id, target = month),
                    account.currency,
                    isApproximate = false,
                ),
                income = DisplayAmount.forcedPositive(
                    flows.income,
                    account.currency,
                    isApproximate = false,
                ),
                yield = DisplayAmount.forcedPositive(
                    flows.yield,
                    account.currency,
                    isApproximate = false,
                ),
                expense = DisplayAmount.forcedNegative(
                    flows.expense,
                    account.currency,
                    isApproximate = false,
                ),
                // The only line whose direction its label withholds.
                adjustment = DisplayAmount.explicitSign(
                    flows.adjustment,
                    account.currency,
                    isApproximate = false,
                ),
                settlement = DisplayAmount.forcedNegative(
                    flows.settlement,
                    account.currency,
                    isApproximate = false,
                ),
                hasMovement = entryRepository.hasEntries(account.id),
                isDefault = account.isDefault,
                yieldsInterest = account.yieldsInterest,
            )
        }
    }

    private val filters = MutableStateFlow(
        AccountsFilters(
            subject = null,
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

        // Everything the other controls leave standing — the universe the axis cuts, and
        // the one the unclassified value is offered against.
        val cuttable = monthTransactions
            .filter(currentFilters.type)
            .filter(currentFilters.recurringOnly)

        val filteredTransactions = cuttable
            .filter(currentFilters.subject)
            .sortedByDescending { it.date }
            .groupBy { it.date }

        // Which emptiness this is comes from the account's whole history, not from the
        // month already cut: an account that moved in another month is a cut with nothing
        // in it, never an account that never moved.
        val listState = when {
            filteredTransactions.isNotEmpty() -> {
                AccountsUiState.ListState.Content(filteredTransactions)
            }

            selectedAccountTransactions.isEmpty() -> AccountsUiState.ListState.EmptyAccount

            else -> AccountsUiState.ListState.EmptyScope(
                canClearFilters = currentFilters.isNotNeutral
            )
        }

        AccountsUiState.Content(
            accounts = accountsPairs.map { it.second },
            domainAccounts = accountsPairs.map { it.first },
            selectedAccountIndex = index,
            selectedAccountId = accountsPairs.getOrNull(index)?.first?.id,
            selectedMonth = month,
            listState = listState,
            categories = categories,
            selectedSubject = currentFilters.subject,
            hasUncategorized = cuttable.any { it.isUncategorized },
            selectedType = currentFilters.type,
            showRecurringOnly = currentFilters.recurringOnly,
            today = today,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = AccountsUiState.Loading(
            selectedMonth = selectedMonth.value,
            today = today,
        )
    )

    fun onAction(action: AccountsAction) = viewModelScope.launch {
        when (action) {
            is AccountsAction.SelectAccount -> {
                selectedAccountId.value = accountRepository
                    .getAllAccounts()[action.index.coerceAtLeast(0)].id
            }

            is AccountsAction.SelectSubject -> {
                filters.value = filters.value.copy(subject = action.subject)
            }

            is AccountsAction.SelectType -> {
                filters.value = filters.value.copy(type = action.type)
            }

            is AccountsAction.ToggleRecurring -> {
                filters.value = filters.value.copy(recurringOnly = action.enabled)
            }

            // The month and the selected account are left alone: they govern the card at
            // the top too, and an action announced as "clear filters" that rewrites those
            // figures would do more than it says.
            is AccountsAction.ClearFilters -> {
                filters.value = AccountsFilters(
                    subject = null,
                    type = null,
                    recurringOnly = false,
                )
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
    val subject: SpendingSubject?,
    val type: TransactionType?,
    val recurringOnly: Boolean,
) {
    /** Whether there is anything for [AccountsAction.ClearFilters] to clear. */
    val isNotNeutral = subject != null || type != null || recurringOnly
}

/**
 * The cut by the analytic axis, over display models. The unclassified case reads the answer
 * the mapper already carried across ([TransactionUi.isUncategorized]) rather than testing
 * `categoryId == null`, which is also true of a transfer and of an orphan dimension.
 */
private fun List<TransactionUi>.filter(subject: SpendingSubject?): List<TransactionUi> = when (subject) {
    null -> this
    is SpendingSubject.Categorized -> filter { it.categoryId == subject.category.id }
    SpendingSubject.Uncategorized -> filter { it.isUncategorized }
}

private fun List<TransactionUi>.filter(type: TransactionType?): List<TransactionUi> {
    if (type == null) return this
    return filter { transaction -> transaction.direction == type }
}

private fun List<TransactionUi>.filter(recurringOnly: Boolean): List<TransactionUi> {
    if (!recurringOnly) return this
    return filter { transaction -> transaction.isRecurring }
}
