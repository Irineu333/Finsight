package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Category

/**
 * Creates one of the user's categories, with its ledger dimension.
 *
 * It owns the three things a caller must not decide for itself: the name is validated
 * (non-empty, and unique across archived categories too) and stored trimmed, and the
 * creation instant is read here — a caller that supplied it could date a category
 * before or after it existed.
 *
 * It takes no identity: there is nothing to resolve, since the category it operates on
 * is the one it brings into existence. [type] is primary state and is stated once, at
 * creation — nothing in the ledger produces it, and no later edit changes it.
 *
 * It answers the category as stored, identity included, because a caller that cannot
 * name what it just created cannot report it either — a surface answering a request
 * from outside has nothing else to point the requester at.
 */
interface CreateCategoryUseCase {
    suspend operator fun invoke(
        name: String,
        iconKey: String,
        type: Category.Type,
    ): Either<Throwable, Category>
}
