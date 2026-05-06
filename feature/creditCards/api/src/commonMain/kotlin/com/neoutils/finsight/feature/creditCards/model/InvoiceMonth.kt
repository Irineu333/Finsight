package com.neoutils.finsight.feature.creditCards.model

import kotlinx.datetime.YearMonth

data class InvoiceMonth(
    val dueMonth: YearMonth,
    val existingInvoice: Invoice?
) {
    val isNew = existingInvoice == null

    val isBlocked = existingInvoice?.status?.isBlocked == true

    val statusColorValue: Long? get() = existingInvoice?.status?.colorValue
}
