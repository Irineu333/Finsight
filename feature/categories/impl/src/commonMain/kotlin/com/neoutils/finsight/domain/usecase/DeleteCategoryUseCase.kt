package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.IBudgetRepository
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Removes a category that was never used, facade and ledger dimension together.
 *
 * A category with movement is refused — see [ArchiveCategoryUseCase]. Removing the
 * pair is the repository's job, because the order is a persistence constraint:
 * the facade references the dimension, so the dimension cannot go first.
 *
 * The same guards account and card deletion carry: a category used by a budget
 * (`budget_categories` is CASCADE) or by a recurring (`recurring.categoryId` is
 * SET_NULL) would be stripped from them silently. Refused so the loss is never
 * created — symmetry the previous version lacked.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
    private val budgetRepository: IBudgetRepository,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> {
        if (entryRepository.hasEntriesForDimension(category.dimensionId)) {
            return AccountException(AccountError.HAS_TRANSACTIONS).left()
        }
        if (budgetRepository.hasBudgetForCategory(category.id)) {
            return AccountException(AccountError.HAS_BUDGET).left()
        }
        if (recurringRepository.hasRecurringForCategory(category.id)) {
            return AccountException(AccountError.HAS_RECURRING).left()
        }
        return catch { categoryRepository.delete(category) }
    }
}
