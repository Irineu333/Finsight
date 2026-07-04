package com.neoutils.finsight.ui.screen.report

import com.neoutils.finsight.ui.screen.report.config.PerspectiveTab
import kotlinx.serialization.Serializable

sealed class ReportRoute {
    @Serializable
    data object Config : ReportRoute()

    @Serializable
    data class Viewer(
        val perspectiveType: PerspectiveTab,
        val accountIds: List<Long> = emptyList(),
        val creditCardId: Long? = null,
        val invoiceIds: List<Long> = emptyList(),
        val startDate: String,
        val endDate: String,
        val includeSpendingByCategory: Boolean,
        val includeIncomeByCategory: Boolean = true,
        val includeTransactionList: Boolean,
    ) : ReportRoute()
}
