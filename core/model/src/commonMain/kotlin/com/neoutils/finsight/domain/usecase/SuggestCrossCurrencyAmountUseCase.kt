package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
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
 * **Between two non-base currencies it answers nothing**, and that is deliberate. The
 * implied amount would exist only by triangulating two rates against the base, which is
 * a guess wearing an observation's clothes — the same reason
 * [HarvestExchangeRateUseCase] refuses to *learn* from such an operation. And the
 * sentence the form would have to say ("by the rate of 05/07") is literally unutterable
 * there: two rates carry two dates, and picking either lies about the other.
 */
class SuggestCrossCurrencyAmountUseCase(
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * @param amount what the user stated on the [from] side.
     * @param on the operation's date — the archive is read *as of* it, never at today's
     * rate, for the same reason a past month's net worth is not recomputed.
     * @return `null` whenever the app has nothing to say: same currency, no rate on or
     * before that date, or neither end being the base.
     */
    suspend operator fun invoke(
        amount: Double,
        from: String,
        to: String,
        on: LocalDate,
    ): CrossCurrencyAmountSuggestion? {
        if (from == to) return null
        if (amount <= 0.0) return null

        val base = baseCurrencyRepository.observe().value

        // A rate is units of the base per one unit of the currency it prices, always in
        // that direction — so the base side of the operation is the one with no rate of
        // its own, and the other side's rate is the whole of the arithmetic.
        val converted = when (base) {
            to -> {
                val rate = exchangeRateRepository.rateAsOf(from, on) ?: return null
                rate to amount * rate.rate
            }

            from -> {
                val rate = exchangeRateRepository.rateAsOf(to, on) ?: return null
                if (rate.rate == 0.0) return null
                rate to amount / rate.rate
            }

            else -> return null
        }

        val (rate, value) = converted

        return CrossCurrencyAmountSuggestion(
            amount = (value * 100).roundToLong() / 100.0,
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
