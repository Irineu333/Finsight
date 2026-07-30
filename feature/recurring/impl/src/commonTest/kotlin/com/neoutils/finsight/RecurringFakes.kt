package com.neoutils.finsight

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf

fun recurring(
    id: Long = 1L,
    type: TransactionType = TransactionType.EXPENSE,
    amount: Double = 100.0,
    createdAt: Long = 0L,
    isArchived: Boolean = false,
) = Recurring(
    id = id,
    type = type,
    amount = amount,
    title = "Rec $id",
    dayOfMonth = 5,
    category = null,
    account = null,
    creditCard = null,
    createdAt = createdAt,
    isArchived = isArchived,
)

class FakeCrashlytics : Crashlytics {
    val recorded = mutableListOf<Throwable>()
    override fun setUserId(id: String?) = Unit
    override fun recordException(e: Throwable) { recorded += e }
}

class FakeRecurringRepository(
    private val hasTransaction: Boolean = false,
    private val updateFailure: Throwable? = null,
) : IRecurringRepository {

    val all = MutableStateFlow<List<Recurring>>(emptyList())
    private val byId = MutableSharedFlow<Recurring?>(replay = 1)

    val updated = mutableListOf<Recurring>()
    val deleted = mutableListOf<Recurring>()

    fun emit(recurring: Recurring?) { byId.tryEmit(recurring) }

    override fun observeAllRecurring(): Flow<List<Recurring>> = all
    override fun observeRecurringById(id: Long): Flow<Recurring?> = byId
    override suspend fun getRecurringById(id: Long): Recurring? = all.value.firstOrNull { it.id == id }
    override suspend fun hasRecurringForAccount(accountId: Long) = false
    override suspend fun hasRecurringForCreditCard(creditCardId: Long) = false
    override suspend fun hasRecurringForCategory(categoryId: Long) = false
    override suspend fun hasTransactionForRecurring(recurringId: Long) = hasTransaction
    override suspend fun insert(recurring: Recurring) = throw NotImplementedError()
    override suspend fun update(recurring: Recurring) {
        updateFailure?.let { throw it }
        updated += recurring
    }
    override suspend fun delete(recurring: Recurring) { deleted += recurring }
}

class FakeBudgetRepository(
    private val hasBudget: Boolean = false,
) : IBudgetRepository {
    override fun observeAllBudgets(): Flow<List<Budget>> = throw NotImplementedError()
    override suspend fun getAllBudgets(): List<Budget> = emptyList()
    override suspend fun insert(budget: Budget) = throw NotImplementedError()
    override suspend fun update(budget: Budget) = throw NotImplementedError()
    override suspend fun delete(budget: Budget) = throw NotImplementedError()
    override suspend fun hasBudgetForCategory(categoryId: Long) = false
    override suspend fun hasBudgetForRecurring(recurringId: Long) = hasBudget
}

/**
 * The chart of accounts, for the one question these screens ask of it: what currency a
 * card's account is in. Everything else throws, so a test that starts depending on more
 * says so instead of quietly passing.
 */
class FakeAccountRepository(
    private val accounts: List<Account> = emptyList(),
) : IAccountRepository {
    override suspend fun getAccountById(accountId: Long): Account? =
        accounts.firstOrNull { it.id == accountId }

    override fun observeAllAccounts(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllAccounts(): List<Account> = accounts
    override suspend fun getAllAccountsIncludingClosed(): List<Account> = accounts
    override fun observeAllAccountsIncludingClosed(): Flow<List<Account>> = flowOf(accounts)
    override suspend fun getAllLedgerAccounts(): List<Account> = accounts
    override fun observeAllLedgerAccounts(): Flow<List<Account>> = flowOf(accounts)
    override fun observeAccountById(accountId: Long): Flow<Account?> =
        flowOf(accounts.firstOrNull { it.id == accountId })

    override suspend fun getDefaultAccount(): Account? = accounts.firstOrNull { it.isDefault }
    override fun observeDefaultAccount(): Flow<Account?> = flowOf(accounts.firstOrNull { it.isDefault })
    override suspend fun getAccountCount(): Int = accounts.size
    override suspend fun insert(account: Account): Long = throw NotImplementedError()
    override suspend fun update(account: Account) = throw NotImplementedError()
    override suspend fun delete(account: Account) = throw NotImplementedError()
    override suspend fun reopen(accountId: Long) = throw NotImplementedError()
}
