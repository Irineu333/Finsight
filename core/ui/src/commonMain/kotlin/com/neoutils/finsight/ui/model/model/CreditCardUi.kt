package com.neoutils.finsight.ui.model

/**
 * A flat, display-ready view of a credit card: the fields the card renders, its current
 * [invoiceUi], and whether its ledger account has movement (the fact behind which retire
 * action the screen offers). Carries no domain graph — a screen that needs the domain
 * `CreditCard` to open a modal resolves it separately, by [cardId].
 */
data class CreditCardUi(
    val cardId: Long,
    val iconKey: String,
    val name: String,
    val closingDay: Int,
    val dueDay: Int,
    val limit: Double,
    val invoiceUi: InvoiceUi?,
    val hasMovement: Boolean = false,
) {
    val retireAction: RetireAction get() = retireActionOf(hasMovement)
}
