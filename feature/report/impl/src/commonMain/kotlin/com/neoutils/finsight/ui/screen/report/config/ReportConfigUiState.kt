package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.PerspectiveTab

sealed class ReportConfigUiState {
    data object Loading : ReportConfigUiState()

    data class Content(
        val config: ReportConfig,
        val accounts: List<Account>,
        val creditCards: List<CreditCard>,
        val invoices: List<Invoice>,
    ) : ReportConfigUiState()
}
