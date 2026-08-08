package com.neoutils.finsight.ui.screen.report.config

import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.extension.DisplayAmount
import kotlinx.serialization.Serializable

sealed class ReportConfigUiState {
    data object Loading : ReportConfigUiState()

    data class Content(
        val config: ReportConfig,
        val accounts: List<Account>,
        val creditCards: List<CreditCardOption>,
        val invoices: List<Invoice>,
    ) : ReportConfigUiState()
}

/**
 * A card the report can be seen from, with its limit already denominated.
 *
 * A card's limit is money on the LIABILITY account the card projects onto (design D17),
 * and that account is deliberately absent from the account facade this screen lists —
 * so the currency is resolved once, here, instead of at the render site.
 */
data class CreditCardOption(
    val card: CreditCard,
    val limit: DisplayAmount,
)

@Serializable
enum class PerspectiveTab {
    ACCOUNT,
    CREDIT_CARD,
}
