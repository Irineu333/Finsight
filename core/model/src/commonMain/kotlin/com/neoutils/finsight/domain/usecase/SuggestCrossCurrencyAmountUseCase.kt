package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate
import kotlin.math.roundToLong

/**
 * What the rate archive implies the other end of an operation is worth — and **when** it
 * learned it.
 *
 * The date is half the answer, not decoration. What the user types into the second field
 * *becomes* a harvested rate (design D11), so filling it in from a fortnight-old quote
 * would write the old rate back as a new observation, silently and in a loop. The form
 * therefore only pre-fills from a rate of the operation's own day and offers everything
 * else as a placeholder that says which day it is from — and [asOf] is what lets it tell
 * the two apart.
 */
data class CrossCurrencyAmountSuggestion(
    /** The other end, in the target currency, rounded to cents. */
    val amount: Double,
    /** The day the rate that implied [amount] is an observation about. */
    val asOf: LocalDate,
)

/**
 * The other end of a cross-currency operation, as far as the archive can say it.
 *
 * This is conversion, so it lives here with the reducer and nowhere near a screen: a
 * form that multiplied by a rate itself would put a second answer to "how much is this
 * worth" one line away from the first.
 *
 * It asks the archive for the pair, and the base currency does not enter into it. Which
 * of several paths answers — direct, inverse, or one pivot — is the archive's declared
 * precedence, with an owner of its own; asking here for the pair the user is actually
 * operating on is what makes two non-base currencies work **without a line of code of
 * its own**.
 */
class SuggestCrossCurrencyAmountUseCase(
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * @param amount what the user stated on the [from] side.
     * @param on the operation's date — the archive is read *as of* it, never at today's
     * rate, for the same reason a past month's net worth is not recomputed.
     * @return `null` whenever the app has nothing to say: same currency, or no path
     * between the two on or before that date.
     */
    suspend operator fun invoke(
        amount: Double,
        from: String,
        to: String,
        on: LocalDate,
    ): CrossCurrencyAmountSuggestion? {
        if (from == to) return null
        if (amount <= 0.0) return null

        // Units of [to] per one unit of [from], which is the pair the operation is
        // about — no base, and therefore no side privileged over the other.
        val rate = exchangeRateRepository.rateBetween(from, to, on) ?: return null

        return CrossCurrencyAmountSuggestion(
            amount = (amount * rate.rate * 100).roundToLong() / 100.0,
            asOf = rate.date,
        )
    }
}

/**
 * The rate an operation applied, read back from its own two ends.
 *
 * It is not a conversion and consults nothing: the two amounts *are* the observation
 * (design D6), which is why no rate is a parameter anywhere on the write path and why a
 * form can show this the moment both fields are filled. Units of the target currency per
 * **one** unit of the source's.
 */
fun impliedRate(sourceAmount: Double, targetAmount: Double): Double? {
    if (sourceAmount <= 0.0 || targetAmount <= 0.0) return null
    return targetAmount / sourceAmount
}
