package com.neoutils.finsight.domain.model

import com.neoutils.finsight.util.dayMonthYear
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

    /**
     * Whether [date], as a form holds it, falls outside what this selection admits.
     *
     * It is a divergence and not an error: the date is the user's word and stands as written,
     * and nothing about the transaction is decided by it — the invoice is. A screen may say
     * so; none may correct it.
     *
     * A date still being typed states nothing, and so states no divergence either.
     */
    fun diverges(date: String): Boolean = runCatching { dayMonthYear.parse(date) }
        .getOrNull()
        ?.let { it !in window } == true
}
