package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository

/**
 * How much of a card's limit is still available, and how much of it the unpaid
 * invoices already take.
 *
 * A card with no declared limit (`limit == 0.0`) reports no usage rather than a
 * division by zero — "unknown", not "full".
 *
 * **Public contract.** A [CreditCard] in, a [Limit] out — plain numbers in the card's
 * own currency, since every invoice of a card is denominated in it (see
 * [CalculateInvoiceUseCase]). Nothing can fail and nothing is presented: no error type
 * and no `UiText`. A concrete class rather than an interface, because it depends only
 * on `:core:*` and on this same `api`.
 */
class CalculateAvailableLimitUseCase(
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    suspend operator fun invoke(
        creditCard: CreditCard
    ): Limit {
        val unpaidInvoices = invoiceRepository.getUnpaidInvoicesByCreditCard(creditCard.id)

        val totalUnpaidAmount = unpaidInvoices.sumOf { invoice ->
            calculateInvoiceUseCase(invoice).coerceAtLeast(0.0)
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

