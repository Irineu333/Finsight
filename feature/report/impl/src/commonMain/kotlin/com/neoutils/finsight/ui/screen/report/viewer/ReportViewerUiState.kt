package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.ui.model.CategorySpendingUi
import com.neoutils.finsight.extension.ConsolidatedFigure
import com.neoutils.finsight.extension.MoneyFigure
import com.neoutils.finsight.ui.model.TransactionUi
import com.neoutils.finsight.util.UiText
import kotlinx.datetime.LocalDate

sealed class ReportViewerUiState {
    data object Loading : ReportViewerUiState()

    /**
     * Every figure of a report spans the whole of its scope — several accounts, several
     * invoices — so each one is a [ConsolidatedFigure]: what it reads as, and the number the
     * card's colour and the exported document's tone are decided by. Reading the sign off the
     * very value that is printed is what keeps text and colour from disagreeing.
     */
    sealed class Stats {

        /**
         * Every figure of this summary — what the card's footer and the exported document's
         * footnote both ask. Declared beside the fields so a line added to a perimeter cannot
         * be left out of the question.
         */
        abstract val figures: List<MoneyFigure>
        /** Every figure carries the sign policy it is displayed with (see the view model). */
        data class Account(
            val startDate: LocalDate,
            val endDate: LocalDate,
            val openingBalance: ConsolidatedFigure,
            val income: ConsolidatedFigure,
            val expense: ConsolidatedFigure,
            val balance: ConsolidatedFigure,
        ) : Stats() {
            override val figures get() = listOf(openingBalance, income, expense, balance).map { it.figure }
        }

        /**
         * [total] is `NATURAL`, not `OWED`: it comes from the ledger's owed figure, which
         * is already positive-as-debt — the same reason `InvoiceSummary.total` is.
         */
        data class Invoice(
            val openingDate: LocalDate,
            val closingDate: LocalDate,
            val expense: ConsolidatedFigure,
            val advancePayment: ConsolidatedFigure,
            val adjustment: ConsolidatedFigure,
            val total: ConsolidatedFigure,
        ) : Stats() {
            override val figures get() = listOf(expense, advancePayment, adjustment, total).map { it.figure }
        }
    }

    data class Content(
        val perspectiveLabel: String,
        val perspectiveBadge: UiText,
        val perspectiveIconKey: String,
        val stats: Stats,
        val categorySpending: List<CategorySpendingUi>?,
        val categoryIncome: List<CategorySpendingUi>?,
        /**
         * Already mapped, under this report's perspective when it has one: the card's
         * ledger account under a credit-card perspective, and nothing under an account
         * one, where several accounts are not a point of view (design D11).
         */
        val transactions: Map<LocalDate, List<TransactionUi>>?,
    ) : ReportViewerUiState()
}
