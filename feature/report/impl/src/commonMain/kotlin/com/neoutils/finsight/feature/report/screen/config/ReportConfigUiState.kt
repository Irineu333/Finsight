package com.neoutils.finsight.feature.report.screen.config

import com.neoutils.finsight.feature.accounts.model.Account
import com.neoutils.finsight.feature.creditCards.model.CreditCard
import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.report.model.PerspectiveTab
import com.neoutils.finsight.feature.report.model.ReportConfig

sealed class ReportConfigUiState {
    data object Loading : ReportConfigUiState()

    data class Content(
        val config: ReportConfig,
        val accounts: List<Account>,
        val creditCards: List<CreditCard>,
        val invoices: List<Invoice>,
    ) : ReportConfigUiState()
}
