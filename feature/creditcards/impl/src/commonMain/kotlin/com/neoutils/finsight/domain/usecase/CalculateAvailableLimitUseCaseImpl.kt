package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
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
            // Floored per invoice, never on the sum: an over-paid cycle owes less than
            // nothing, and letting it net against its neighbour would report limit the
            // card never granted.
            val owedByStatus = unpaidByCard[creditCard.id]
                .orEmpty()
                .groupBy { invoice -> invoice.status }
                .mapValues { (_, invoices) ->
                    invoices.sumOf { (owedByInvoice[it.id] ?: 0.0).coerceAtLeast(0.0) }
                }

            creditCard.id to creditCard.limitOf(owedByStatus)
        }
    }

    /**
     * `OPEN`, `CLOSED` and `FUTURE` exhaust what arrives here, and only because the read
     * above states "unpaid" as `status NOT IN ('PAID', 'RETROACTIVE')`. Widening that
     * predicate without widening this split would drop the new state out of the total
     * silently — the two are one rule expressed in two places.
     *
     * A card with no limit declared has no fraction to be in use: dividing by it would
     * be dividing by zero, and reporting 100% would claim a ceiling the user never set.
     */
    private fun CreditCard.limitOf(owedByStatus: Map<Invoice.Status, Double>): Limit {
        val open = owedByStatus[Invoice.Status.OPEN] ?: 0.0
        val closed = owedByStatus[Invoice.Status.CLOSED] ?: 0.0
        val future = owedByStatus[Invoice.Status.FUTURE] ?: 0.0
        val committed = open + closed + future

        return Limit(
            openAmount = open,
            closedAmount = closed,
            futureAmount = future,
            available = (limit - committed).coerceAtLeast(0.0),
            usage = if (limit != 0.0) (committed / limit).coerceIn(0.0, 1.0) else 0.0,
        )
    }
}
