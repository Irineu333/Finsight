package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Budget
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.LimitType
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Shared across the budget use-case tests in this package: records the writes and
 * resolves by id out of the seeded list, so the use cases resolve the identity when
 * they run — exactly as they do against the real store.
 */
class RecordingBudgetRepository(
    private val existing: List<Budget> = emptyList(),
) : IBudgetRepository {

    val inserted = mutableListOf<Budget>()
    val updated = mutableListOf<Budget>()
    val deleted = mutableListOf<Long>()

    override fun observeAllBudgets(): Flow<List<Budget>> = flowOf(existing)
    override suspend fun getAllBudgets(): List<Budget> = existing

    override suspend fun insert(budget: Budget): Long {
        inserted += budget
        return inserted.size.toLong()
    }

    override suspend fun update(budget: Budget) { updated += budget }
    override suspend fun delete(budget: Budget) { deleted += budget.id }
    override suspend fun hasBudgetForCategory(categoryId: Long) = false
    override suspend fun hasBudgetForRecurring(recurringId: Long) = false
}

fun testCategory(
    id: Long = 1L,
    name: String = "Food",
) = Category(
    id = id,
    name = name,
    icon = CategoryLazyIcon("food"),
    type = Category.Type.EXPENSE,
    createdAt = 0L,
    dimensionId = id * 10,
)

fun testBudget(
    id: Long = 1L,
    title: String = "Groceries",
    categories: List<Category> = listOf(testCategory()),
    amount: Double = 500.0,
    currency: String = "BRL",
    limitType: LimitType = LimitType.FIXED,
    percentage: Double? = null,
    recurringId: Long? = null,
) = Budget(
    id = id,
    title = title,
    categories = categories,
    iconKey = "shopping",
    amount = amount,
    currency = currency,
    limitType = limitType,
    percentage = percentage,
    recurringId = recurringId,
    createdAt = 42L,
)

fun testRecurring(
    id: Long = 7L,
    amount: Double = 3_000.0,
) = Recurring(
    id = id,
    type = TransactionType.INCOME,
    amount = amount,
    title = "Salary",
    dayOfMonth = 5,
    category = null,
    account = null,
    creditCard = null,
    createdAt = 0L,
)
