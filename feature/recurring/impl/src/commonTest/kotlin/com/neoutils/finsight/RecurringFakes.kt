package com.neoutils.finsight

import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

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
    override suspend fun update(recurring: Recurring) { updated += recurring }
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
