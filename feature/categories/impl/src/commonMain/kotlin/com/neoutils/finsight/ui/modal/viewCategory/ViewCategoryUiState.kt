@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.ui.modal.viewCategory

import com.neoutils.finsight.domain.model.Category
import com.neoutils.finsight.ui.model.RetireAction
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.YearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

sealed interface ViewCategoryUiState {

    data object Loading : ViewCategoryUiState

    data object Error : ViewCategoryUiState

    data class Content(
        val category: Category,
        // Which retire action this screen may offer — the same rule accounts and
        // cards use, so the three facades cannot drift.
        val retireAction: RetireAction = RetireAction.DELETE,
        val selectedYearMonth: YearMonth = Clock.System.now().toYearMonth(),
        val totalAmount: Double = 0.0,
        val transactionCount: Int = 0,
    ) : ViewCategoryUiState
}
