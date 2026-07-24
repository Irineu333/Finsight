package com.neoutils.finsight.ui.model

data class InvoiceOverview(
    val invoiceId: Long,
    val creditCardName: String,
    val expense: Double,
    val advancePayment: Double,
    val adjustment: Double,
    val total: Double,
) {
    val mustShowAdjustment = adjustment != 0.0
}
