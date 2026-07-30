package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.ExchangeRate
import com.neoutils.finsight.domain.repository.IBaseCurrencyRepository
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import kotlinx.datetime.LocalDate

/**
 * The rate a cross-currency operation applied, kept as a line of the rate history.
 *
 * What is recorded is a **fact about a date**, not a property of the operation: the two ends
 * the user typed imply a quote, and the app would otherwise ask them to type it again in the
 * rates screen. The operation itself gains no rate field — `balanced-ledger` forbids it — and
 * the rate outlives it (design D27), which is why nothing here links the two.
 *
 * It lives in this layer rather than in either feature because the transfer and the two
 * invoice payments are the same derivation seen from three screens, in two `impl` modules
 * that cannot see each other. It lives outside the ledger for the same reason conversion
 * does: a rate is not `Σ entries`.
 *
 * Three cases record nothing, and none of them is an error:
 *
 * - **the same currency on both ends** — there is no quote to learn;
 * - **neither end is the base** — a USD→EUR transfer under a BRL base implies a cross rate,
 *   and the app holds no matrix of pairs (design D11); deriving one against the base from it
 *   would invent the very number this change refuses to invent;
 * - **a non-positive end** — no quote is derivable from zero, and the forms already refuse it.
 */
class CollectOperationRateUseCase(
    private val exchangeRateRepository: IExchangeRateRepository,
    private val baseCurrencyRepository: IBaseCurrencyRepository,
) {

    /**
     * Records the quote implied by [sourceAmount] in [sourceCurrency] buying
     * [destinationAmount] in [destinationCurrency] on [date].
     *
     * The base currency is read as a snapshot on purpose: this decides what a *past* operation
     * taught, once, at the moment it is written — it renders nothing and follows nothing.
     */
    suspend operator fun invoke(
        sourceCurrency: String,
        sourceAmount: Double,
        destinationCurrency: String,
        destinationAmount: Double,
        date: LocalDate,
    ) {
        if (sourceCurrency == destinationCurrency) return
        if (sourceAmount <= 0.0 || destinationAmount <= 0.0) return

        val base = baseCurrencyRepository.current()

        // A rate says how many units of the base one unit of the other currency is worth, so
        // whichever end is the base is the numerator — the direction of the operation does
        // not enter into it.
        val (currency, rate) = when (base) {
            sourceCurrency -> destinationCurrency to sourceAmount / destinationAmount
            destinationCurrency -> sourceCurrency to destinationAmount / sourceAmount
            else -> return
        }

        exchangeRateRepository.record(
            ExchangeRate(
                currency = currency,
                date = date,
                rate = rate,
                source = ExchangeRate.Source.OPERATION,
            )
        )
    }
}
