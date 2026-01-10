package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.util.UiText

class ValidateCategoryNameUseCase(
    private val repository: ICategoryRepository
) {
    suspend operator fun invoke(
        name: String,
    ): UiText? {

        if (name.isEmpty()) {
            return UiText.Raw("O nome da categoria não pode ser vazio.")
        }

        if (hasDuplicateName(name)) {
            return UiText.Raw("Já existe uma categoria com esse nome.")
        }

        return null
    }

    private suspend fun hasDuplicateName(
        name: String,
    ): Boolean {

        val categories = repository.getAllCategories()

        return categories.any { it.name.equals(name.trim(), true) }
    }
}