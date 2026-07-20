package com.neoutils.finsight.domain.model

import kotlinx.datetime.YearMonth

data class InvoiceMonthSelection(
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isClosedToNewExpenses = existingInvoice?.status?.isClosedToNewExpenses == true
}
