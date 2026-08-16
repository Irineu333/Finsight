package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category

/**
 * Brings an archived category back into circulation — the exact inverse of
 * [ArchiveCategoryUseCase]. Reversible and innocuous: it flips `isArchived` back on
 * the facade and nothing else, so no invariant can refuse it and no confirmation is
 * warranted. The category reappears in its selectors, in the active listings, and as
 * a budget option; its entries stay classified on its dimension throughout.
 */
interface UnarchiveCategoryUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The category is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `CategoryError.NOT_FOUND`, because reopening is a blind
     * `UPDATE` by id and would otherwise report a category that came back from nowhere.
     */
    suspend operator fun invoke(categoryId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the category. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = invoke(category.id)
}
