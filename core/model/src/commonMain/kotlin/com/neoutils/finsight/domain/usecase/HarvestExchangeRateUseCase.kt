package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate
import kotlin.math.absoluteValue

/**
 * Every transaction that crosses currencies registers its own rate.
 *
 * It costs nothing: the two ends already exist, so the rate applied is a quotient of
 * facts the user already gave, and he never types the same number twice. Nothing is
 * written **on the transaction** — the operation has no rate field anywhere on the
 * write path (design D6). What is written is a line of the archive, which then outlives
 * the operation that revealed it (design D27).
 */
class HarvestExchangeRateUseCase(
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * Registers the rate the two ends of an operation imply, on the pair they crossed
     * and in the direction it happened.
     *
     * @return the rate registered, or `null` when there was none to learn.
     *
     * **Every crossing teaches.** A USD → EUR transfer under a BRL base used to be
     * discarded — not because that was a rule of the domain, but because a row could not
     * say which pair it spoke about, so an observation off the base's axis had nowhere to
     * live. It has one now, and refusing it would be code written to throw away
     * information the user already gave.
     *
     * The user's rate of the same date is not consulted and not overwritten: the two
     * are different rows of the archive — the unique key includes the origin — and
     * precedence is settled once, on the read side. This one is what the operation
     * actually observed, and it stays true whether or not the user disagrees with it.
     */
    suspend operator fun invoke(
        sourceAmount: Double,
        sourceCurrency: String,
        targetAmount: Double,
        targetCurrency: String,
        date: LocalDate,
    ): ExchangeRate? {
        if (sourceCurrency == targetCurrency) return null

        val denominator = sourceAmount.absoluteValue
        if (denominator == 0.0) return null

        // The full quotient, in the direction the operation happened: units of the
        // target currency per one unit of the source's. Never the rounded form the rates
        // screen shows — that is formatting, with an owner of its own, and storing it
        // would make every later reading a compounding loss. And never canonicalised
        // either: inverting to store would keep a number nobody measured.
        val rate = ExchangeRate(
            currency = sourceCurrency,
            counterCurrency = targetCurrency,
            date = date,
            rate = targetAmount.absoluteValue / denominator,
            source = ExchangeRate.Source.DERIVED,
        )
        exchangeRateRepository.save(rate)
        return rate
    }
}
