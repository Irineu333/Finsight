package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.domain.model.Category
import org.jetbrains.compose.resources.StringResource

sealed class CategoriesUiState {

    data object Loading : CategoriesUiState()

    /**
     * The database holds no category at all — the only case that earns the big CTA
     * empty-state. A filter that merely happens to be empty is [Content] with no
     * sections (design D10). [filter] survives so the FAB still knows its default type.
     */
    data class Empty(
        val filter: CategoryFilter = CategoryFilter.ACTIVE,
    ) : CategoriesUiState()

    data class Content(
        val filter: CategoryFilter,
        val sections: List<Section>,
    ) : CategoriesUiState()

    /**
     * A rendered block of the list: an optional [header] (only ACTIVE splits into
     * Despesas/Receitas) and its [categories]. An empty section is never emitted.
     */
    data class Section(
        val header: StringResource?,
        val categories: List<Category>,
    )
}
