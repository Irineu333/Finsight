package com.neoutils.finsight.ui.screen.categories

import com.neoutils.finsight.domain.model.Category

/**
 * The one selector on the categories screen, mixing two axes on purpose: status
 * ([ACTIVE], [ARCHIVED]) and type ([EXPENSE], [INCOME]). "Ativas" rather than "Todas"
 * keeps it honest — archived categories are excluded from the first three views.
 */
enum class CategoryFilter {
    ACTIVE,
    EXPENSE,
    INCOME,
    ARCHIVED,
}

/**
 * Which type a new category defaults to when created from this filter: the typed
 * filters pin their type, the mixed views fall back to expense (design D5).
 */
val CategoryFilter.fabInitialType: Category.Type
    get() = when (this) {
        CategoryFilter.INCOME -> Category.Type.INCOME
        else -> Category.Type.EXPENSE
    }
