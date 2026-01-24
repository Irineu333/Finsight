package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.errors.ValidateCategoryNameErrors
import com.neoutils.finance.domain.exception.CategoryException
import com.neoutils.finance.domain.repository.ICategoryRepository

private val errors = ValidateCategoryNameErrors()

class ValidateCategoryNameUseCase(
    private val repository: ICategoryRepository
) {
    suspend operator fun invoke(
        name: String,
        ignoreId: Long? = null
    ): Result<String> {
        if (name.isEmpty()) {
            return Result.failure(CategoryException(errors.nameRequired))
        }

        if (hasDuplicateName(name, ignoreId)) {
            return Result.failure(CategoryException(errors.nameAlreadyExists))
        }

        return Result.success(name)
    }

    private suspend fun hasDuplicateName(name: String, ignoreId: Long?): Boolean {
        val categories = repository.getAllCategories()
        return categories.any {
            it.name.equals(name.trim(), ignoreCase = true) && it.id != ignoreId
        }
    }
}
