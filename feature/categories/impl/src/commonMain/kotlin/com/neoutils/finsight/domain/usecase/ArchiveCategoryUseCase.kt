package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository

/**
 * Retires a category that has movement. The facade stays so past transactions keep
 * showing its name; it is only marked archived, which is what removes it from the
 * pickers and from `Budget.categories`.
 *
 * Unlike an account or a card, a category has no chart-of-accounts row to close
 * (design D4), so the flag is its own. Nothing else changes: closing a category
 * never depended on a balance and was never checked at the write boundary — a
 * category's balance is a period total, not money sitting anywhere.
 */
class ArchiveCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = catch {
        categoryRepository.archive(category.id)
    }
}
