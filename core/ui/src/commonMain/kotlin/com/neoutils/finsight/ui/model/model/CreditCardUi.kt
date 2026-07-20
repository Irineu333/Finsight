package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.CreditCard

data class CreditCardUi(
    val creditCard: CreditCard,
    val invoiceUi: InvoiceUi?,
    // Whether the card's ledger account has movement — the fact behind which
    // action the screen offers. See [RetireAction].
    val hasMovement: Boolean = false,
) {
    val retireAction: RetireAction get() = retireActionOf(hasMovement)
}
