@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.SystemCategoryKey
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.category_yield
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import com.neoutils.finsight.util.AppIcon
import com.neoutils.finsight.util.UiText
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Guarantees the yield category exists, and hands back the dimension every read uses
 * to tell yield apart from ordinary income.
 *
 * On demand, in the spirit of the system accounts: the boundary that needs the row
 * creates it, rather than the install seeding one nobody asked for. A user who never
 * declares a yielding account never gets the category.
 *
 * Idempotent by key, not by name — the second declaration finds the first one's
 * category however the user has since renamed it, so there is never a second.
 */
class EnsureYieldCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
) {
    suspend operator fun invoke(): Category {
        categoryRepository.getCategoryBySystemKey(SystemCategoryKey.YIELD)?.let { return it }

        categoryRepository.insert(
            Category(
                name = UiText.Res(Res.string.category_yield).asString(),
                icon = CategoryLazyIcon(AppIcon.SAVINGS.key),
                type = Category.Type.INCOME,
                createdAt = Clock.System.now().toEpochMilliseconds(),
                systemKey = SystemCategoryKey.YIELD,
            )
        )

        // Re-read rather than return what was built: the dimension is minted by the
        // store on insert, exactly like the id, and it is the whole point of the call.
        return requireNotNull(categoryRepository.getCategoryBySystemKey(SystemCategoryKey.YIELD)) {
            "The yield category was just inserted and could not be read back."
        }
    }
}
