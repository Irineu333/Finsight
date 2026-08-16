package com.neoutils.finsight.domain.usecase

import arrow.core.Either

/**
 * Edits a category — its name and its icon, which is everything about it that is not
 * identity or history.
 *
 * The type is absent from the signature on purpose: `Category.type` is the user's
 * declaration at creation and the axis every entry already classified under it was
 * read against, so flipping it would silently restate what past movement meant.
 *
 * Validation and trimming live here, not in the caller: the stored name is the
 * validated one, and the uniqueness check ignores the category's own identity so an
 * edit may keep its name.
 */
interface UpdateCategoryUseCase {

    /**
     * The category is resolved **when the operation runs**, so the edit lands on the
     * category as it is at that instant; an identity that matches nothing is refused
     * with `CategoryError.NOT_FOUND` and nothing is written.
     */
    suspend operator fun invoke(
        categoryId: Long,
        name: String,
        iconKey: String,
    ): Either<Throwable, Unit>
}
