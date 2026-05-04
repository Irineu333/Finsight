package com.neoutils.finsight.feature.creditCards.mapper

import com.neoutils.finsight.feature.creditCards.model.Invoice
import com.neoutils.finsight.feature.creditCards.model.InvoiceUi
import com.neoutils.finsight.feature.creditCards.repository.ICreditCardRepository
import com.neoutils.finsight.feature.creditCards.usecase.CalculateAvailableLimitUseCase
import com.neoutils.finsight.feature.creditCards.usecase.CalculateInvoiceUseCase

class InvoiceUiMapper(
    private val creditCardRepository: ICreditCardRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
    private val calculateAvailableLimitUseCase: CalculateAvailableLimitUseCase,
) : IInvoiceUiMapper {
    override suspend fun toUi(
        invoice: Invoice,
    ): InvoiceUi {

        val creditCard = requireNotNull(
            creditCardRepository.getCreditCardById(invoice.creditCardId)
        ) { "CreditCard ${invoice.creditCardId} not found for invoice ${invoice.id}" }

        val outstandingDebt = calculateInvoiceUseCase(
            invoiceId = invoice.id,
        ).coerceAtLeast(0.0)

        val limit = calculateAvailableLimitUseCase(creditCard)

        val showProgress = outstandingDebt > 0 && limit.usage != 0.0

        return InvoiceUi(
            invoice = invoice,
            creditCard = creditCard,
            amount = outstandingDebt,
            totalUnpaidAmount = limit.totalUnpaidAmount,
            availableLimit = limit.available,
            usagePercentage = if (showProgress) limit.usage else 0.0,
            showProgress = showProgress,
        )
    }
}
