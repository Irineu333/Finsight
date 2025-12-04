package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import kotlinx.coroutines.flow.Flow

class GetCategoriesUseCase(
    private val repository: ICategoryRepository
) {
    operator fun invoke(type: Category.Type? = null): Flow<List<Category>> {
        return if (type != null) {
            repository.getCategoriesByType(type)
        } else {
            repository.getAllCategories()
        }
    }
}
