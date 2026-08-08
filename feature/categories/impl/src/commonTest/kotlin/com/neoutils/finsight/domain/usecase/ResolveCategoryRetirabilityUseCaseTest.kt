package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.RetireError
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.model.SystemCategoryKey
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

    private val yieldCategory = Category(
        id = 2, name = "Rendimentos", icon = CategoryLazyIcon("savings"),
        type = Category.Type.INCOME, createdAt = 0L, dimensionId = 20,
        systemKey = SystemCategoryKey.YIELD,
    )

    private fun useCase(
        hasEntries: Boolean = false,
        hasBudget: Boolean = false,
        hasRecurring: Boolean = false,
        hasYieldingAccount: Boolean = false,
    ) = ResolveCategoryRetirabilityUseCase(
        entryRepository = FakeEntries(hasEntries),
        budgetRepository = FakeBudget(hasBudget),
        recurringRepository = FakeRecurring(hasRecurring),
        accountRepository = FakeAccounts(hasYieldingAccount),
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

    @Test
    fun `a declared yielding account forces archive with HAS_YIELDING_ACCOUNTS`() = runTest {
        val result = assertIs<CategoryRetirability.MustArchive>(
            useCase(hasYieldingAccount = true)(yieldCategory)
        )
        assertEquals(RetireError.HAS_YIELDING_ACCOUNTS, result.reason)
    }

    @Test
    fun `with the last declaration turned off the yield category is deletable again`() = runTest {
        // The protection is a dependent, not an immutability: being a system category
        // grants nothing by itself.
        assertEquals(CategoryRetirability.Deletable, useCase()(yieldCategory))
    }

    @Test
    fun `an ordinary category is unaffected by a yielding account`() = runTest {
        assertEquals(CategoryRetirability.Deletable, useCase(hasYieldingAccount = true)(category))
    }

    @Test
    fun `the yield category with movement is archived by the movement guard`() = runTest {
        // No new outcome for the delete-vs-archive pair: it is the same MustArchive
        // every other dependent produces, and the first guard tripped names it.
        val result = assertIs<CategoryRetirability.MustArchive>(
            useCase(hasEntries = true)(yieldCategory)
        )
        assertEquals(RetireError.HAS_TRANSACTIONS, result.reason)
    }
}
