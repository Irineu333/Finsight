package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.extension.ConsolidatedAmount
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    sealed class Stats {
        /**
         * Every figure carries the sign policy it is displayed with (see the view model).
         *
         * They are **consolidated** figures and not single amounts: a scope spans
         * accounts, and accounts may differ in currency, so what the card and the export
         * show is whatever the reducer could reduce (design D9, D13).
         */
        data class Account(
            val startDate: LocalDate,
            val endDate: LocalDate,
            val openingBalance: ConsolidatedAmount,
            val income: ConsolidatedAmount,
            val expense: ConsolidatedAmount,
            val balance: ConsolidatedAmount,
        ) : Stats()

        /**
         * [total] is `NATURAL`, not `OWED`: it comes from the ledger's owed figure, which
         * is already positive-as-debt — the same reason `InvoiceSummary.total` is.
         *
         * A single amount each, and not a figure: an invoice report is scoped to one
         * card, so every line is denominated by that card's ledger account (design D17).
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
