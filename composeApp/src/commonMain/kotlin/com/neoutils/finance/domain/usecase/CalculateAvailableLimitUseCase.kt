package com.neoutils.finance.domain.usecase

import com.neoutils.finance.domain.model.CreditCard
import com.neoutils.finance.domain.repository.ICreditCardRepository
import com.neoutils.finance.domain.repository.IInvoiceRepository

class CalculateAvailableLimitUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    suspend operator fun invoke(
        creditCard: CreditCard
    ): Limit {
        val unpaidInvoices = invoiceRepository.getUnpaidInvoicesByCreditCard(creditCard.id)

        val totalUnpaidAmount = unpaidInvoices.sumOf { invoice ->
            calculateInvoiceUseCase(invoice.id).coerceAtLeast(0.0)
        }

        if (creditCard.limit != 0.0) {
            return Limit(
                totalUnpaidAmount = totalUnpaidAmount,
                available = (creditCard.limit - totalUnpaidAmount).coerceAtLeast(0.0),
                usage = (totalUnpaidAmount / creditCard.limit).coerceIn(0.0, 1.0),
            )
        }

        return Limit(
            totalUnpaidAmount = totalUnpaidAmount,
            available = (creditCard.limit - totalUnpaidAmount).coerceAtLeast(0.0),
            usage = 0.0,
        )
    }
}

data class Limit(
    val totalUnpaidAmount: Double,
    val available: Double,
    val usage: Double,
)

