package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Operation
import kotlinx.datetime.LocalDate

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    data class Content(
        val perspectiveLabel: String,
        val perspectiveIconKey: String,
        val startDate: LocalDate,
        val endDate: LocalDate,
        val initialBalance: Double,
        val income: Double,
        val expense: Double,
        val balance: Double,
        val categorySpending: List<CategorySpending>?,
        val transactions: Map<LocalDate, List<Operation>>?,
    ) : ReportViewerUiState()
}
