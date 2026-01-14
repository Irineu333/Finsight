package com.neoutils.finance.domain.model

import com.neoutils.finance.util.DateFormats
import kotlinx.datetime.YearMonth

private val formats = DateFormats()

data class InvoiceMonthSelection(
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isBlocked = existingInvoice?.status?.isBlocked == true

    val label = existingInvoice?.label ?: "${formats.yearMonth.format(dueMonth)} • Nova"

    val statusColor get() = existingInvoice?.status?.color
}
