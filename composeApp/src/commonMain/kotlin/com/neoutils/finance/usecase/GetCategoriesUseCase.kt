package com.neoutils.finance.usecase

import com.neoutils.finance.data.Category
import com.neoutils.finance.data.CategoryRepository
import kotlinx.coroutines.flow.Flow

class GetCategoriesUseCase(
    private val repository: CategoryRepository
) {
    operator fun invoke(type: Category.CategoryType? = null): Flow<List<Category>> {
        return if (type != null) {
            repository.getCategoriesByType(type)
        } else {
            repository.getAllCategories()
        }
    }
}
