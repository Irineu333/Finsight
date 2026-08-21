package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.CreditCard

/**
 * How much of a card's limit is still spendable: what holds it — split by the cycle that
 * holds it — what is left, and the fraction in use.
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

/**
 * What holds a card's limit, and what is left of it.
 *
 * The committed figure is **split by the cycle that holds it**, because the three are
 * different facts about the user's money and one number cannot say which is which:
 * [closedAmount] is what is due to be paid, [futureAmount] is what an instalment already
 * committed to cycles that have not opened yet, and [openAmount] is what the current
 * cycle has accrued so far. All three hold limit from the moment they are posted — that
 * is what an instalment does — which is why [committedAmount] is their sum and is the
 * figure [available] is taken against.
 *
 * Each cycle is floored at zero on its own: an over-paid one owes less than nothing, and
 * letting it net against its neighbour would report limit the card never granted.
 */
data class Limit(
    val openAmount: Double,
    val closedAmount: Double,
    val futureAmount: Double,
    val available: Double,
    val usage: Double,
) {
    /** The three cycles together — everything holding the limit right now. */
    val committedAmount: Double get() = openAmount + closedAmount + futureAmount

    companion object {
        /** Nothing owed, nothing available, nothing in use — what absence reads as. */
        val NONE = Limit(
            openAmount = 0.0,
            closedAmount = 0.0,
            futureAmount = 0.0,
            available = 0.0,
            usage = 0.0,
        )
    }
}
