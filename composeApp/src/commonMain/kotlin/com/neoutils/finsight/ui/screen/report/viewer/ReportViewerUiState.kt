package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    data class Content(
        val perspectiveLabel: String,
        val dateRangeLabel: String,
        val income: Double,
        val expense: Double,
        val balance: Double,
        val categorySpending: List<CategorySpending>?,
        val transactions: Map<kotlinx.datetime.LocalDate, List<Operation>>?,
    ) : ReportViewerUiState()
}
