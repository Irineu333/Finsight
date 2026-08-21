package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.repository.ICategoryRepository

class UnarchiveCategoryUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
) : UnarchiveCategoryUseCase {

    override suspend fun invoke(categoryId: Long): Either<Throwable, Unit> = either {
        // Reopening is a blind `UPDATE` by id, which touches nothing when the id
        // matches nothing: without this the caller would be told the category came back.
        ensureNotNull(catch { categoryRepository.getCategoryById(categoryId) }.bind()) {
            CategoryException(CategoryError.NOT_FOUND)
        }

        catch { categoryRepository.unarchive(categoryId) }.bind()
    }
}
