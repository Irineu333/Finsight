package com.neoutils.finance.ui.mapper

import com.neoutils.finance.domain.model.Invoice
import com.neoutils.finance.domain.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finance.domain.usecase.CalculateInvoiceUseCase
import com.neoutils.finance.ui.model.InvoiceUi

class InvoiceUiMapper(
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val calculateAvailableLimitUseCase: CalculateAvailableLimitUseCase,
) {
    suspend fun toUi(
        invoice: Invoice,
    ): InvoiceUi {

        val amount = calculateInvoiceUseCase(invoiceId = invoice.id)

        val displayAmount = amount.coerceAtLeast(0.0)
        val limit = calculateAvailableLimitUseCase(invoice.creditCard)

        if (displayAmount > 0 && limit.usage != 0.0) {
            return InvoiceUi(
                invoice = invoice,
                amount = displayAmount,
                totalUnpaidAmount = limit.totalUnpaidAmount,
                availableLimit = limit.available,
                usagePercentage = limit.usage,
                showProgress = true,
            )
        }

        return InvoiceUi(
            invoice = invoice,
            amount = displayAmount,
            totalUnpaidAmount = limit.totalUnpaidAmount,
            availableLimit = limit.available,
            usagePercentage = 0.0,
            showProgress = false,
        )
    }
}

