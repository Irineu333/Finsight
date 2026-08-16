package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category

/**
 * Removes a category that was never used, facade and ledger dimension together.
 *
 * A category with any dependent is refused — see [ResolveCategoryRetirabilityUseCase],
 * the single owner of that rule — and archived instead ([ArchiveCategoryUseCase]).
 * Removing the pair is the repository's job, because the order is a persistence
 * constraint: the facade references the dimension, so the dimension cannot go first.
 */
interface DeleteCategoryUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The category is resolved **when the operation runs**, so the guards read the
     * category as it is at that instant rather than as a caller once loaded it; an
     * identity that matches nothing is refused with `CategoryError.NOT_FOUND` and
     * nothing is removed.
     */
    suspend operator fun invoke(categoryId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the category. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = invoke(category.id)
}
