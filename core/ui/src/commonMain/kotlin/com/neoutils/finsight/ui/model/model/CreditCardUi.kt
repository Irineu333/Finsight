package com.neoutils.finsight.ui.model

/**
 * A flat, display-ready view of a credit card: the fields the card renders, its current
 * [invoiceUi], and whether it [mustPreserve] rather than be deleted — the fact behind
 * which retire action the screen offers. Carries no domain graph — a screen that needs
 * the domain `CreditCard` to open a modal resolves it separately, by [cardId].
 */
data class CreditCardUi(
    val cardId: Long,
    val iconKey: String,
    val name: String,
    val closingDay: Int,
    val dueDay: Int,
    val limit: Double,
    val invoiceUi: InvoiceUi?,
    // Everything DeleteCreditCardUseCase refuses on: movement (entries) or a recurring
    // still pointing at the card. Deriving this from entries alone would let the screen
    // offer a delete the use case then refuses.
    val mustPreserve: Boolean = false,
) {
    val retireAction: RetireAction get() = retireActionOf(mustPreserve)
}
