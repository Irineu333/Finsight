package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import kotlinx.serialization.Serializable

sealed class ReportConfigUiState {
    data object Loading : ReportConfigUiState()

    data class Content(
        val config: ReportConfig,
        val accounts: List<Account>,
        val creditCards: List<CreditCard>,
        val invoices: List<Invoice>,
    ) : ReportConfigUiState()
}

@Serializable
enum class PerspectiveTab {
    ACCOUNT,
    CREDIT_CARD,
}
