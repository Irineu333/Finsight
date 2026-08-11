package com.neoutils.finsight.domain.model

import kotlinx.datetime.YearMonth

data class InvoiceMonthSelection(
    val creditCard: CreditCard,
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isClosedToNewExpenses = existingInvoice?.status?.isClosedToNewExpenses == true

    /**
     * The span this selection admits purchases in.
     *
     * The card is carried for this: navigating to a month with no invoice yet is the ordinary
     * case, and a selection with no invoice still has a window — the one the invoice would be
     * created with.
     */
    val window = existingInvoice?.window ?: creditCard.invoiceWindowFor(dueMonth)
}
