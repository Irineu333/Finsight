package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon

class UpdateCategoryUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
) : UpdateCategoryUseCase {

    override suspend fun invoke(
        categoryId: Long,
        name: String,
        iconKey: String,
    ): Either<Throwable, Unit> = either {
        // Resolved here and not received: the edit is applied to the category as it is
        // at this instant, so everything the caller did not name — its type, its
        // dimension, whether it is archived — survives untouched.
        val category = ensureNotNull(catch { categoryRepository.getCategoryById(categoryId) }.bind()) {
            CategoryException(CategoryError.NOT_FOUND)
        }

        // Its own identity is ignored by the uniqueness check, so an edit that keeps
        // the name is not refused as a clash with itself.
        val validName = validateCategoryName(name, ignoreId = categoryId)
            .mapLeft(::CategoryException)
            .bind()

        catch {
            categoryRepository.update(
                category.copy(
                    name = validName,
                    icon = CategoryLazyIcon(iconKey),
                )
            )
        }.bind()
    }
}
