package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.repository.IEntryRepository

/**
 * Removes a category that was never used, facade and ledger account together.
 *
 * A category with movement is refused — see [ArchiveCategoryUseCase]. Removing the
 * pair is the repository's job, because the order is a persistence constraint:
 * the facade references the account, so the account cannot go first.
 */
class DeleteCategoryUseCase(
    private val categoryRepository: ICategoryRepository,
    private val entryRepository: IEntryRepository,
) {
    suspend operator fun invoke(category: Category): Either<Throwable, Unit> {
        if (entryRepository.hasEntries(category.accountId)) {
            return AccountException(AccountError.HAS_TRANSACTIONS).left()
        }
        return catch { categoryRepository.delete(category) }
    }
}
