@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.exception.CategoryException
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.ui.icons.CategoryLazyIcon
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CreateCategoryUseCaseImpl(
    private val categoryRepository: ICategoryRepository,
    private val validateCategoryName: ValidateCategoryNameUseCase,
) : CreateCategoryUseCase {

    override suspend fun invoke(
        name: String,
        iconKey: String,
        type: Category.Type,
    ): Either<Throwable, Category> = either {
        // The validator is the single owner of "what a category name is", trimming
        // included — the stored name is the one it returns, never the raw input.
        val validName = validateCategoryName(name).mapLeft(::CategoryException).bind()

        val category = Category(
            name = validName,
            icon = CategoryLazyIcon(iconKey),
            type = type,
            createdAt = Clock.System.now().toEpochMilliseconds(),
        )

        catch { category.copy(id = categoryRepository.insert(category)) }.bind()
    }
}
