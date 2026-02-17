@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finance.domain.model.Category
import com.neoutils.finance.domain.repository.ICategoryRepository
import com.neoutils.finance.ui.icons.CategoryLazyIcon
import com.neoutils.finance.util.CategoryIcon
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CategoriesViewModel(
    private val categoryRepository: ICategoryRepository,
) : ViewModel() {

    val uiState = categoryRepository
        .observeAllCategories()
        .map { categories ->
            CategoriesUiState(
                categories = categories.sortedBy { it.name },
            )
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = CategoriesUiState()
        )

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.CreateDefaultCategories -> {
                createDefaultCategories()
            }
        }
    }

    private fun createDefaultCategories() = viewModelScope.launch {
        try {
            val createdAtBase = Clock.System.now().toEpochMilliseconds()

            defaultCategories
                .forEachIndexed { index, template ->
                    categoryRepository.insert(
                        template.toCategory(createdAt = createdAtBase + index)
                    )
                }
        } catch (_: Exception) {
            // TODO: register exception
        }
    }

    private fun DefaultCategoryTemplate.toCategory(createdAt: Long): Category {
        return Category(
            name = name,
            icon = CategoryLazyIcon(icon.key),
            type = type,
            createdAt = createdAt,
        )
    }

    private data class DefaultCategoryTemplate(
        val name: String,
        val icon: CategoryIcon,
        val type: Category.Type,
    )

    private val defaultCategories = listOf(
        DefaultCategoryTemplate(
            name = "Salario",
            icon = CategoryIcon.MONEY,
            type = Category.Type.INCOME,
        ),
        DefaultCategoryTemplate(
            name = "Freelance",
            icon = CategoryIcon.WORK,
            type = Category.Type.INCOME,
        ),
        DefaultCategoryTemplate(
            name = "Investimentos",
            icon = CategoryIcon.BUSINESS,
            type = Category.Type.INCOME,
        ),
        DefaultCategoryTemplate(
            name = "Beneficios",
            icon = CategoryIcon.GIFT,
            type = Category.Type.INCOME,
        ),
        DefaultCategoryTemplate(
            name = "Reembolso",
            icon = CategoryIcon.DEFAULT,
            type = Category.Type.INCOME,
        ),
        DefaultCategoryTemplate(
            name = "Moradia",
            icon = CategoryIcon.HOME,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Alimentacao",
            icon = CategoryIcon.RESTAURANT,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Mercado",
            icon = CategoryIcon.SHOPPING_CART,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Transporte",
            icon = CategoryIcon.CAR,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Saude",
            icon = CategoryIcon.HEALTH,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Educacao",
            icon = CategoryIcon.SCHOOL,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Lazer",
            icon = CategoryIcon.MOVIE,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Assinaturas",
            icon = CategoryIcon.WIFI,
            type = Category.Type.EXPENSE,
        ),
        DefaultCategoryTemplate(
            name = "Viagem",
            icon = CategoryIcon.FLIGHT,
            type = Category.Type.EXPENSE,
        ),
    )
}
