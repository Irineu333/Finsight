package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    sealed class Stats {
        data class Account(
            val startDate: LocalDate,
            val endDate: LocalDate,
            val initialBalance: Double,
            val income: Double,
            val expense: Double,
            val balance: Double,
        ) : Stats()

        data class Invoice(
            val invoice: com.neoutils.finsight.domain.model.Invoice,
            val expense: Double,
            val advancePayment: Double,
            val adjustment: Double,
            val total: Double,
        ) : Stats()
    }

    data class Content(
        val perspectiveLabel: String,
        val perspectiveBadge: UiText,
        val perspectiveIconKey: String,
        val stats: Stats,
        val categorySpending: List<CategorySpending>?,
        val transactions: Map<LocalDate, List<Operation>>?,
    ) : ReportViewerUiState()
}
