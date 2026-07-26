package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    sealed class Stats {
        /** Every figure carries the sign policy it is displayed with (see the view model). */
        data class Account(
            val startDate: LocalDate,
            val endDate: LocalDate,
            val openingBalance: DisplayAmount,
            val income: DisplayAmount,
            val expense: DisplayAmount,
            val balance: DisplayAmount,
        ) : Stats()

        /**
         * [total] is `NATURAL`, not `OWED`: it comes from the ledger's owed figure, which
         * is already positive-as-debt — the same reason `InvoiceSummary.total` is.
         */
        data class Invoice(
            val openingDate: LocalDate,
            val closingDate: LocalDate,
            val expense: DisplayAmount,
            val advancePayment: DisplayAmount,
            val adjustment: DisplayAmount,
            val total: DisplayAmount,
        ) : Stats()
    }

    data class Content(
        val perspectiveLabel: String,
        val perspectiveBadge: UiText,
        val perspectiveIconKey: String,
        val stats: Stats,
        val categorySpending: List<CategorySpending>?,
        val categoryIncome: List<CategorySpending>?,
        /**
         * Already mapped, under this report's perspective when it has one: the card's
         * ledger account under a credit-card perspective, and nothing under an account
         * one, where several accounts are not a point of view (design D11).
         */
        val transactions: Map<LocalDate, List<TransactionUi>>?,
    ) : ReportViewerUiState()
}
