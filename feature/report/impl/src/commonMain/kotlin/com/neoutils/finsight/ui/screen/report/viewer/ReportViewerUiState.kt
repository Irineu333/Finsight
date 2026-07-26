package com.neoutils.finsight.ui.screen.report.viewer

import com.neoutils.finsight.domain.model.CategorySpending
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.ui.model.TransactionFacadeLookup
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
        /**
         * The account this report is read through, when it is read through **one**: the
         * card's ledger account under a credit-card perspective. An account perspective is
         * a *list* of accounts, and several accounts are not a point of view — a transfer
         * between two of them has both legs inside the perimeter — so it stays null there,
         * and that is the right answer rather than a missing one.
         */
        val perspectiveAccountId: Long? = null,
        val perspectiveBadge: UiText,
        val perspectiveIconKey: String,
        val stats: Stats,
        val categorySpending: List<CategorySpending>?,
        val categoryIncome: List<CategorySpending>?,
        val transactions: Map<LocalDate, List<Transaction>>?,
        val facadeLookup: TransactionFacadeLookup = TransactionFacadeLookup.EMPTY,
    ) : ReportViewerUiState()
}
