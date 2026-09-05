package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.exception.RetireException
import com.neoutils.finsight.domain.model.CategoryRetirability
import com.neoutils.finsight.domain.repository.ICategoryRepository

class DeleteCategoryUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val resolveRetirability: ResolveCategoryRetirabilityUseCase,
) : DeleteCategoryUseCase {

    override suspend fun invoke(categoryId: Long): Either<Throwable, Unit> = either {
        // Resolved here and not received: removal is irreversible and takes the ledger
        // dimension with it, so the category has to be the one that exists now — a
        // screen that loaded it minutes ago may be holding a category already gone.
        val category = ensureNotNull(catch { categoryRepository.getCategoryById(categoryId) }.bind()) {
            CategoryException(CategoryError.NOT_FOUND)
        }

        when (val retirability = resolveRetirability(categoryId).bind()) {
            is CategoryRetirability.MustArchive -> raise(RetireException(retirability.reason))
            CategoryRetirability.Deletable -> catch { categoryRepository.delete(category) }.bind()
        }
    }
}
