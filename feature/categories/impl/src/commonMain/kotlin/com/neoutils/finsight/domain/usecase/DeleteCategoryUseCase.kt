package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.repository.ICategoryRepository

/**
 * Removes a category that was never used, facade and ledger dimension together.
 *
 * A category with any dependent is refused — see [ResolveCategoryRetirabilityUseCase],
 * the single owner of that rule — and archived instead ([ArchiveCategoryUseCase]).
 * Removing the pair is the repository's job, because the order is a persistence
 * constraint: the facade references the dimension, so the dimension cannot go first.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
    private val resolveRetirability: ResolveCategoryRetirabilityUseCase,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> =
        when (val retirability = resolveRetirability(category)) {
            is CategoryRetirability.MustArchive -> RetireException(retirability.reason).left()
            CategoryRetirability.Deletable -> catch { categoryRepository.delete(category) }
        }
}
