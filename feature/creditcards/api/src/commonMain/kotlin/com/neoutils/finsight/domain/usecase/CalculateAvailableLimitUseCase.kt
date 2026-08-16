package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard

/**
 * How much of a card's limit is still spendable: what its unpaid invoices owe, what is
 * left of the limit, and the fraction of it in use.
 *
 * **The plural form is the one that carries the implementation**, because this figure
 * is asked of a whole list far more often than of a single card, and asking per card
 * over a list already loaded costs one invoice query and one ledger read *per card*
 * (design D7). It receives the identities and answers indexed by them, exactly as
 * `IEntryRepository.owedByDimensionByCurrency` does: N cards cost one read, not N.
 *
 * A card that does not exist is **absent from the map**, and the caller reads it as
 * [Limit.NONE] — the same answer a card with no unpaid invoice and no limit gets. This
 * is a read, so there is no refusal to make: nothing is written and nothing is lost by
 * answering the neutral figure.
 *
 * Every figure here is in the card's own currency, which is the one its `LIABILITY`
 * account states and never changes. Nothing is consolidated: adding the limits of two
 * cards in different currencies is conversion, and conversion lives above the ledger.
 */
interface CalculateAvailableLimitUseCase {

    /** The canonical form. Cards that do not resolve are absent from the answer. */
    suspend operator fun invoke(creditCardIds: Collection<Long>): Map<Long, Limit>

    /**
     * One card by identity. Not another figure, so not another implementation — and
     * an identity that resolves to nothing reads as [Limit.NONE].
     */
    suspend operator fun invoke(creditCardId: Long): Limit =
        invoke(listOf(creditCardId))[creditCardId] ?: Limit.NONE

    /**
     * The convenience for a caller that already holds the card. It extracts the
     * identity and delegates, so the figure is read against the card as it is stored
     * rather than as the caller remembers it.
     */
    suspend operator fun invoke(creditCard: CreditCard): Limit = invoke(creditCard.id)
}

data class Limit(
    val totalUnpaidAmount: Double,
    val available: Double,
    val usage: Double,
) {
    companion object {
        /** Nothing owed, nothing available, nothing in use — what absence reads as. */
        val NONE = Limit(totalUnpaidAmount = 0.0, available = 0.0, usage = 0.0)
    }
}
