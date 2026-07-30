package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate
import kotlin.math.round

/**
 * What the other end of a cross-currency operation would be worth, by the rate the app knows.
 *
 * It is a **suggestion about a field**, never a figure: nothing consolidated passes through
 * here, and what the user types wins over it always. The reason it exists at all is that a
 * form asking for two amounts asks twice for something the app usually already knows.
 *
 * [ConversionSuggestion.isFromOperationDate] is the whole of the policy, and it is here rather
 * than in a ViewModel because it has a consequence in the domain: the value the user leaves in
 * the field **becomes** a collected rate (design D11), so pre-filling it from a two-week-old
 * quote would write the old rate back as a new one, silently and in a loop. Only a quote from
 * the operation's own date may seed the field; an older one belongs in a placeholder, dated,
 * where accepting it is a deliberate act.
 *
 * Answers `null` when there is nothing to suggest — same currency on both ends, neither end
 * being the base (design D11 holds no matrix of pairs), or no rate known by that date. That is
 * a defined state and not a failure: the user types the second amount, and the operation
 * collects the rate that was missing.
 */
class SuggestConvertedAmountUseCase(
    private val exchangeRateRepository: IExchangeRateRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
) {

    suspend operator fun invoke(
        fromCurrency: String,
        toCurrency: String,
        amount: Double,
        date: LocalDate,
    ): ConversionSuggestion? {
        if (fromCurrency == toCurrency) return null
        if (amount <= 0.0) return null

        val base = baseCurrencyRepository.current()

        val foreign = when (base) {
            fromCurrency -> toCurrency
            toCurrency -> fromCurrency
            else -> return null
        }

        val quote = exchangeRateRepository.rateOn(foreign, date) ?: return null
        if (quote.rate <= 0.0) return null

        // The quote is base-per-foreign, so leaving the base divides and arriving at it
        // multiplies. Rounded to cents because a money field holds cents and nothing else —
        // seeding it with more precision than it can hold would make the first keystroke
        // rewrite the number the user was shown.
        val converted = if (fromCurrency == base) amount / quote.rate else amount * quote.rate

        return ConversionSuggestion(
            amount = round(converted * 100) / 100,
            rate = quote,
            isFromOperationDate = quote.date == date,
        )
    }
}

/**
 * A suggested second amount and the quote it came from — the quote travels with it because the
 * form has to say which one it used, and asking again would be a second decision.
 */
data class ConversionSuggestion(
    val amount: Double,
    val rate: ExchangeRate,
    val isFromOperationDate: Boolean,
)
