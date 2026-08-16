package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.repository.ICreditCardRepository
import com.neoutils.finsight.domain.repository.IInvoiceRepository

class CalculateAvailableLimitUseCaseImpl(
    private val creditCardRepository: ICreditCardRepository,
    private val invoiceRepository: IInvoiceRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) : CalculateAvailableLimitUseCase {

    override suspend fun invoke(creditCardIds: Collection<Long>): Map<Long, Limit> {
        if (creditCardIds.isEmpty()) return emptyMap()

        val wanted = creditCardIds.toSet()

        // Three reads, whatever the number of cards: the cards, their unpaid invoices,
        // and what those invoices owe. Closed cards are kept — an archived card still
        // has a limit and a bill, and leaving it out would answer the neutral figure
        // for a card that does exist.
        val creditCards = creditCardRepository
            .getAllCreditCardsIncludingClosed()
            .filter { it.id in wanted }

        val unpaidByCard = invoiceRepository.getUnpaidInvoicesByCreditCards(wanted)
        val owedByInvoice = calculateInvoiceUseCase(unpaidByCard.values.flatten())

        return creditCards.associate { creditCard ->
            val totalUnpaidAmount = unpaidByCard[creditCard.id]
                .orEmpty()
                .sumOf { invoice -> (owedByInvoice[invoice.id] ?: 0.0).coerceAtLeast(0.0) }

            creditCard.id to creditCard.limitOf(totalUnpaidAmount)
        }
    }

    /**
     * A card with no limit declared has no fraction to be in use: dividing by it would
     * be dividing by zero, and reporting 100% would claim a ceiling the user never set.
     */
    private fun CreditCard.limitOf(totalUnpaidAmount: Double) = Limit(
        totalUnpaidAmount = totalUnpaidAmount,
        available = (limit - totalUnpaidAmount).coerceAtLeast(0.0),
        usage = if (limit != 0.0) (totalUnpaidAmount / limit).coerceIn(0.0, 1.0) else 0.0,
    )
}
