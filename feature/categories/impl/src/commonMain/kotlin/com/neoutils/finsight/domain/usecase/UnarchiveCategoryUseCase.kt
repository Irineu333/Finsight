package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository

/**
 * Brings an archived category back into circulation — the exact inverse of
 * [ArchiveCategoryUseCase]. Reversible and innocuous: it flips `isArchived` back on
 * the facade and nothing else, so no invariant can refuse it and no confirmation is
 * warranted. The category reappears in its selectors, in the active listings, and as
 * a budget option; its entries stay classified on its dimension throughout.
 */
class UnarchiveCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> = catch {
        categoryRepository.unarchive(category.id)
    }
}
