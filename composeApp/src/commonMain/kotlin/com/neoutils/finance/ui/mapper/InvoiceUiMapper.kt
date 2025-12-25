package com.neoutils.finance.ui.mapper

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.ui.model.InvoiceUi

class InvoiceUiMapper(
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    suspend fun toUi(
        invoice: Invoice,
    ): InvoiceUi {

        val amount = calculateInvoiceUseCase(invoiceId = invoice.id)

        val displayAmount = amount.coerceAtLeast(0.0)
        val availableLimit = (invoice.creditCard.limit - amount).coerceAtLeast(0.0)

        if (displayAmount > 0 && invoice.creditCard.limit > 0) {
            return InvoiceUi(
                invoice = invoice,
                amount = displayAmount,
                availableLimit = availableLimit,
                usagePercentage = (amount / invoice.creditCard.limit).coerceIn(0.0, 1.0),
                showProgress = true,
            )
        }

        return InvoiceUi(
            invoice = invoice,
            amount = displayAmount,
            availableLimit = availableLimit,
            usagePercentage = 0.0,
            showProgress = false,
        )
    }
}

