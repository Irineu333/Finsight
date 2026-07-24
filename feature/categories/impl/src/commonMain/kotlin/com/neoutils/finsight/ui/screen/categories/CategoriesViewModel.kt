package com.neoutils.finsight.ui.screen.categories

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.neoutils.finsight.domain.crashlytics.Crashlytics
import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.domain.repository.ICategoryRepository
import com.neoutils.finsight.domain.usecase.CreateDefaultCategoriesUseCase
import com.neoutils.finsight.resources.Res
import com.neoutils.finsight.resources.categories_expense
import com.neoutils.finsight.resources.categories_income
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.StringResource

class CategoriesViewModel(
    private val categoryRepository: ICategoryRepository,
    private val createDefaultCategories: CreateDefaultCategoriesUseCase,
    private val crashlytics: Crashlytics,
) : ViewModel() {

    private val filter = MutableStateFlow(CategoryFilter.ACTIVE)

    val uiState = combine(
        // Archived ones are needed here — the ARCHIVED view surfaces them — but they
        // stay out of the active views and never leak back into any selector.
        categoryRepository.observeAllCategoriesIncludingClosed(),
        filter,
    ) { categories, filter ->
        // The big CTA is for a genuinely empty database only; a filter that merely has
        // nothing to show is Content with no sections (design D10).
        if (categories.isEmpty()) {
            CategoriesUiState.Empty(filter = filter)
        } else {
            CategoriesUiState.Content(
                filter = filter,
                sections = sectionsFor(filter, categories),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CategoriesUiState.Loading,
    )

    private fun sectionsFor(
        filter: CategoryFilter,
        categories: List<Category>,
    ): List<CategoriesUiState.Section> {
        // "Active" is `!isArchived`, mirroring the DAO's OPEN_CATEGORIES predicate (B1).
        val active = categories.filter { !it.isArchived }
        return when (filter) {
            CategoryFilter.ACTIVE -> listOfNotNull(
                section(Res.string.categories_expense, active.filter { it.type == Category.Type.EXPENSE }),
                section(Res.string.categories_income, active.filter { it.type == Category.Type.INCOME }),
            )

            CategoryFilter.EXPENSE -> listOfNotNull(
                section(header = null, active.filter { it.type == Category.Type.EXPENSE }),
            )

            CategoryFilter.INCOME -> listOfNotNull(
                section(header = null, active.filter { it.type == Category.Type.INCOME }),
            )

            CategoryFilter.ARCHIVED -> listOfNotNull(
                section(header = null, categories.filter { it.isArchived }),
            )
        }
    }

    /** A section with no categories is omitted, so an empty type never draws a header. */
    private fun section(
        header: StringResource?,
        categories: List<Category>,
    ): CategoriesUiState.Section? = categories
        .takeIf { it.isNotEmpty() }
        ?.let { CategoriesUiState.Section(header, it.sortedBy { category -> category.name }) }

    fun onAction(action: CategoriesAction) {
        when (action) {
            CategoriesAction.CreateDefaultCategories -> viewModelScope.launch {
                createDefaultCategories().onLeft {
                    crashlytics.recordException(it)
                }
            }

            is CategoriesAction.SelectFilter -> {
                filter.value = action.filter
            }
        }
    }
}
