package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.model.CategoryRetirability

/**
 * Resolves, in one place, whether a category may be deleted or must be archived. The
 * four guards — movement on its dimension, a budget, a recurring, an account that
 * declares it yields — each name their own `RetireError`, so [DeleteCategoryUseCase]
 * and the view can consume one decision instead of re-deriving it. One owner decides;
 * consumers only read.
 *
 * The yield guard is a dependent like any other, not a state of immutability: being a
 * system category confers no protection by itself, and the last account to stop
 * yielding makes the category ordinary again (design D4). Nothing here adds a third
 * outcome to the delete-vs-archive pair, so no screen learns a new case.
 */
interface ResolveCategoryRetirabilityUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The category is resolved **when the question is asked**, so the guards read the
     * dependents as they are at that instant. An identity that matches nothing is
     * refused with `CategoryError.NOT_FOUND`: the pair of outcomes answers "delete or
     * archive" for a category that exists, and a missing one is neither.
     */
    suspend operator fun invoke(categoryId: Long): Either<Throwable, CategoryRetirability>

    /**
     * The convenience for a caller that already holds the category. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(category: Category): Either<Throwable, CategoryRetirability> =
        invoke(category.id)
}
