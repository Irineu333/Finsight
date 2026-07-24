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
        // Trim once, at the boundary: empty and uniqueness must judge the same string,
        // or "  " would pass as non-empty and then clash with a real name inconsistently.
        val trimmed = name.trim()

        if (trimmed.isEmpty()) {
            return CategoryError.EMPTY_NAME.left()
        }

        // Uniqueness spans closed categories too: closing keeps the name, and history
        // still renders it. Two "Mercado" side by side, one grey, is not a name. The
        // check is a single EXISTS query instead of scanning every category per keystroke.
        if (repository.existsByName(trimmed, ignoreId ?: 0L)) {
            return CategoryError.ALREADY_EXIST.left()
        }

        return trimmed.right()
    }
}
