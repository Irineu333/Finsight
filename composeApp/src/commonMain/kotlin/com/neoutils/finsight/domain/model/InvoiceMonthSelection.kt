package com.neoutils.finsight.domain.model

import kotlinx.datetime.YearMonth

data class InvoiceMonthSelection(
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isBlocked = existingInvoice?.status?.isBlocked == true

    val statusColor get() = existingInvoice?.status?.color
}
