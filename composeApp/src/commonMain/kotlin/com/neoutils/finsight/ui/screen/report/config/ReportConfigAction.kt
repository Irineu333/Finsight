package com.neoutils.finsight.ui.screen.report.config

import kotlinx.datetime.LocalDate

sealed class ReportConfigAction {
    data class SelectPerspective(val tab: PerspectiveTab) : ReportConfigAction()
    data class ToggleAccount(val accountId: Long) : ReportConfigAction()
    data class SelectCreditCard(val creditCardId: Long?) : ReportConfigAction()
    data class ToggleInvoice(val invoiceId: Long) : ReportConfigAction()
    data class SelectStartDate(val date: LocalDate) : ReportConfigAction()
    data class SelectEndDate(val date: LocalDate) : ReportConfigAction()
    data class ToggleSpendingByCategory(val enabled: Boolean) : ReportConfigAction()
    data class ToggleIncomeByCategory(val enabled: Boolean) : ReportConfigAction()
    data class ToggleTransactionList(val enabled: Boolean) : ReportConfigAction()
}
