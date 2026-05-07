package com.neoutils.finsight.feature.categories.modal.viewCategory

import com.neoutils.finsight.feature.categories.model.Category
import kotlinx.datetime.YearMonth

sealed class ViewCategoryUiState {
    data object Loading : ViewCategoryUiState()
    data object Error : ViewCategoryUiState()
    data class Content(
        val category: Category,
        val selectedYearMonth: YearMonth,
        val totalAmount: Double,
        val transactionCount: Int,
    ) : ViewCategoryUiState()
}
