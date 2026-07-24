package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

/**
 * The single owner of "may this category be deleted, or must it be archived?". Each
 * guard names its own reason; with none tripped the category is deletable.
 */
class ResolveCategoryRetirabilityUseCaseTest {

    private val category = Category(
        id = 1, name = "Food", icon = CategoryLazyIcon("food"),
        type = Category.Type.EXPENSE, createdAt = 0L, dimensionId = 10,
    )

    private fun useCase(
        hasEntries: Boolean = false,
        hasBudget: Boolean = false,
        hasRecurring: Boolean = false,
    ) = ResolveCategoryRetirabilityUseCase(
        entryRepository = FakeEntries(hasEntries),
        budgetRepository = FakeBudget(hasBudget),
        recurringRepository = FakeRecurring(hasRecurring),
    )

    @Test
    fun `no dependents is deletable`() = runTest {
        assertEquals(CategoryRetirability.Deletable, useCase()(category))
    }

    @Test
    fun `movement forces archive with HAS_TRANSACTIONS`() = runTest {
        val result = assertIs<CategoryRetirability.MustArchive>(useCase(hasEntries = true)(category))
        assertEquals(RetireError.HAS_TRANSACTIONS, result.reason)
    }

    @Test
    fun `a budget forces archive with HAS_BUDGET`() = runTest {
        val result = assertIs<CategoryRetirability.MustArchive>(useCase(hasBudget = true)(category))
        assertEquals(RetireError.HAS_BUDGET, result.reason)
    }

    @Test
    fun `a recurring forces archive with HAS_RECURRING`() = runTest {
        val result = assertIs<CategoryRetirability.MustArchive>(useCase(hasRecurring = true)(category))
        assertEquals(RetireError.HAS_RECURRING, result.reason)
    }
}
