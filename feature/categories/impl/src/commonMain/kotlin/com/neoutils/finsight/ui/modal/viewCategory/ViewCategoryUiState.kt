package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.extension.ConsolidatedAmount
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
        // A category has no currency of its own — it is a dimension, and its entries
        // may sit in several (design D13), so what the row shows is a consolidated
        // figure and never a number wearing the base currency (design D29).
        val totalAmount: ConsolidatedAmount,
        val transactionCount: Int,
    ) : ViewCategoryUiState
}
