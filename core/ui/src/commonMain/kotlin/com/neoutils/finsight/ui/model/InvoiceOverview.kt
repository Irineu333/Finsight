package com.neoutils.finsight.ui.model

import com.neoutils.finsight.domain.model.Invoice

data class InvoiceOverview(
    val invoiceId: Long,
    val creditCardName: String,
    val invoiceStatus: Invoice.Status,
    val expense: Double,
    val advancePayment: Double,
    val adjustment: Double,
    val total: Double,
) {
    val mustShowAdjustment = adjustment != 0.0
}
