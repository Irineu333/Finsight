package com.neoutils.finsight.domain.model

import kotlinx.datetime.YearMonth
import com.neoutils.finsight.core.domain.model.Invoice

data class InvoiceMonthSelection(
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isBlocked = existingInvoice?.status?.isBlocked == true

    val statusColorValue: Long? get() = existingInvoice?.status?.colorValue
}
