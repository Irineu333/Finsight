package com.neoutils.finsight.ui.model

/**
 * A flat, display-ready view of an account's period figures. Carries no domain
 * graph and does no calculation — every value is derived from the ledger by the
 * ViewModel and handed in already computed. The domain [Account] itself (for the
 * icon, name and actions) is rendered by the component that receives it directly.
 */
data class AccountUi(
    val id: Long,
    val openingBalance: Double,
    val balance: Double,
    val income: Double,
    val expense: Double,
    val adjustment: Double,
    val invoicePayment: Double,
)
