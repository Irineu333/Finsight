package com.neoutils.finsight.ui.screen.report

import com.neoutils.finsight.domain.model.PerspectiveTab
import kotlinx.datetime.LocalDate

data class ReportViewerParams(
    val perspectiveType: PerspectiveTab,
    val accountIds: List<Long> = emptyList(),
    val creditCardId: Long? = null,
    val invoiceIds: List<Long> = emptyList(),
    val startDate: LocalDate,
    val endDate: LocalDate,
    val includeSpendingByCategory: Boolean,
    val includeIncomeByCategory: Boolean = true,
    val includeTransactionList: Boolean,
)

fun ReportViewerParams.toRoute(): ReportRoute.Viewer = ReportRoute.Viewer(
    perspectiveType = perspectiveType,
    accountIds = accountIds,
    creditCardId = creditCardId,
    invoiceIds = invoiceIds,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    includeSpendingByCategory = includeSpendingByCategory,
    includeIncomeByCategory = includeIncomeByCategory,
    includeTransactionList = includeTransactionList,
)

fun ReportRoute.Viewer.toParams(): ReportViewerParams = ReportViewerParams(
    perspectiveType = perspectiveType,
    accountIds = accountIds,
    creditCardId = creditCardId,
    invoiceIds = invoiceIds,
    startDate = LocalDate.parse(startDate),
    endDate = LocalDate.parse(endDate),
    includeSpendingByCategory = includeSpendingByCategory,
    includeIncomeByCategory = includeIncomeByCategory,
    includeTransactionList = includeTransactionList,
)
