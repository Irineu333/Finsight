package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category

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
interface ArchiveCategoryUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The category is resolved **when the operation runs**: archiving is a blind
     * `UPDATE` by id, which touches nothing when the id matches nothing, so an
     * identity that matches nothing is refused with `CategoryError.NOT_FOUND` rather
     * than reported as an archive that happened.
     */
    suspend operator fun invoke(categoryId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the category. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = invoke(category.id)
}
