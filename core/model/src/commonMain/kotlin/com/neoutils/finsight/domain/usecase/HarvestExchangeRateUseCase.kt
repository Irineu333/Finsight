package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
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
    private val baseCurrencyRepository: IBaseCurrencyRepository,
    private val exchangeRateRepository: IExchangeRateRepository,
) {

    /**
     * Registers the rate the two ends of an operation imply, when one of them is the
     * base currency.
     *
     * @return the rate registered, or `null` when there was none to learn.
     *
     * **A leg in neither currency teaches nothing.** A USD → EUR transfer under a BRL
     * base implies no rate against the base, and inventing one by triangulating today's
     * others would be a guess wearing an observation's clothes. It is an explicit
     * Non-Goal, not an omission.
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

        val base = baseCurrencyRepository.observe().value
        val (baseSide, other, otherCurrency) = when (base) {
            sourceCurrency -> Triple(sourceAmount, targetAmount, targetCurrency)
            targetCurrency -> Triple(targetAmount, sourceAmount, sourceCurrency)
            else -> return null
        }

        val denominator = other.absoluteValue
        if (denominator == 0.0) return null

        // The full quotient, in the fixed direction currency → base: units of the base
        // per one unit of the other currency. Never the rounded form the rates screen
        // shows — that is formatting, with an owner of its own, and storing it would
        // make every later reading a compounding loss.
        val rate = ExchangeRate(
            currency = otherCurrency,
            date = date,
            rate = baseSide.absoluteValue / denominator,
            source = ExchangeRate.Source.DERIVED,
        )
        exchangeRateRepository.save(rate)
        return rate
    }
}
