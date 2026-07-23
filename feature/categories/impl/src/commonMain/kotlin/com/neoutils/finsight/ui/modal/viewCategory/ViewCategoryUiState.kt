package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.model.RetireAction
import kotlinx.datetime.YearMonth

sealed interface ViewCategoryUiState {

    data object Loading : ViewCategoryUiState

    data object Error : ViewCategoryUiState

    data class Content(
        val category: Category,
        // Which retire action this screen may offer — the same rule accounts and
        // cards use, so the three facades cannot drift.
        val retireAction: RetireAction,
        val selectedYearMonth: YearMonth,
        val totalAmount: Double,
        val transactionCount: Int,
    ) : ViewCategoryUiState
}
