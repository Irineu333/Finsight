package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.left
import arrow.core.right
import com.neoutils.finsight.domain.error.CategoryError
import com.neoutils.finsight.domain.repository.ICategoryRepository

class ValidateCategoryNameUseCase(
    private val repository: ICategoryRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Either<CategoryError, String> {
        if (name.isEmpty()) {
            return CategoryError.EMPTY_NAME.left()
        }

        if (hasDuplicateName(name, ignoreId)) {
            return CategoryError.ALREADY_EXIST.left()
        }

        return name.right()
    }

    private suspend fun hasDuplicateName(
        name: String,
        ignoreId: Long?
    ): Boolean {
        // TODO: improve this
        return repository.getAllCategories().any { creditCard ->
            creditCard.name.equals(name.trim(), ignoreCase = true) &&
                    creditCard.id != ignoreId
        }
    }
}
