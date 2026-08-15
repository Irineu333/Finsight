package com.neoutils.finsight.mcp.contract

import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.MoneyByCurrency
import com.neoutils.finsight.domain.repository.IExchangeRateRepository
import com.neoutils.finsight.domain.usecase.ConsolidateMoneyUseCase
import com.neoutils.finsight.extension.DisplayAmount
import com.neoutils.finsight.extension.displaySign
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline
import kotlin.math.roundToLong

/**
 * How many minor units make one major unit, as an exponent of ten.
 *
 * **Two, for every currency, and it is not an omission.** The app's whole arithmetic is
 * base 100 — the ledger persists cents in a `Long` — and the currency registry refuses a
 * code the platform declares with any other number of decimal places
 * (`CurrencyError.UNSUPPORTED_DECIMALS`). So the scale is a promise the domain already
 * enforces, and reading it back off the platform per code would let a currency the app
 * refused to create describe money the app cannot hold.
 *
 * It travels in every amount all the same: a consumer that had to know the exponent from
 * somewhere else would eventually get it from its own table, and a wrong exponent is a
 * hundredfold error that reads as a plausible number.
 */
const val MONEY_SCALE: Int = 2

private const val MINOR_UNITS_PER_MAJOR = 100.0

/**
 * One denominated amount, in the only form money crosses this boundary in: the currency,
 * an **integer in the minor unit**, and the scale that relates the two.
 *
 * Integer and not a decimal: a JSON number is a `double` to most consumers, and an agent
 * that does arithmetic on `1234.56` reports a cent that never existed.
 */
@Serializable
data class MoneyAmount(
    /** ISO 4217 code. The amount means nothing without it, so the two never travel apart. */
    val currency: String,
    /** The amount in the minor unit of [currency] — cents —, signed as displayed. */
    val minorUnits: Long,
    /** The exponent of ten relating the minor unit to the major one. Always [MONEY_SCALE]. */
    val scale: Int = MONEY_SCALE,
    /**
     * The same amount as the user would read it.
     *
     * **For display only, and for nothing else.** It is produced by a locale-aware
     * formatter, so its decimal separator, its grouping and its symbol placement all move
     * with the machine the server runs on. A consumer that parsed it back would be
     * deriving money from a presentation decision. [minorUnits] is the value; this is a
     * caption.
     *
     * `null` whenever the surface producing the amount has no formatter to hand, which is
     * not a defect: the caption is optional precisely because it is not the data.
     */
    val formattedForDisplayOnly: String? = null,
) {
    companion object {
        /** The amount of a value in the **major** unit — the form every ledger read answers in. */
        fun of(value: Double, currency: String, formattedForDisplayOnly: String? = null) = MoneyAmount(
            currency = currency,
            minorUnits = (value * MINOR_UNITS_PER_MAJOR).roundToLong(),
            formattedForDisplayOnly = formattedForDisplayOnly,
        )
    }
}

/**
 * The sign every amount on this surface is read with — **the display sign, never the
 * ledger's**.
 *
 * The ledger is debit-positive: an expense sums positive there and an income sums
 * negative, because that is what makes `Σ = 0` a sentence about a transaction. Letting
 * that convention out of the ledger would have an agent report a month of spending as
 * income, and it is the kind of error that only surfaces after it has been told to the
 * user.
 *
 * Built from an [AccountType] and from nothing else, so no call site invents a rule of
 * its own — and the two branches are the ledger's own vocabulary:
 *
 * - A **monetary** account (`ASSET`, `LIABILITY`) reads with `AccountType.displaySign`,
 *   the ledger's own display rule: a balance you hold reads positive, and a debt reads as
 *   the magnitude owed.
 * - A **nominal** account (`INCOME`, `EXPENSE`) states its balance from its own side of
 *   the entry, and this surface states it from the user's: an expense is money leaving,
 *   so it reads negative, and an income reads positive. That is one factor, `-1`, for
 *   both — the credit-positive reading of the same natural balance — and it is exactly
 *   the sign the monetary leg of that same transaction carries. Reporting the money side
 *   is the whole rule.
 */
@JvmInline
value class DisplaySign private constructor(val factor: Int) {

    operator fun times(value: Double): Double = value * factor

    companion object {
        /** The sign a figure denominated in an account of [type] reads with. */
        fun of(type: AccountType): DisplaySign = when {
            type.isNominal -> DisplaySign(-1)
            else -> DisplaySign(type.displaySign)
        }

        /**
         * The sign of money **held** — a balance, a net worth, an opening balance. It is
         * `of(ASSET)`, spelled out so a figure that spans the whole chart does not have to
         * name an account type it is not about.
         */
        val ofMoneyHeld: DisplaySign = of(AccountType.ASSET)
    }
}

/**
 * A figure a read that **can span accounts** answers with: one amount per currency, plus
 * the consolidated value as a sibling.
 *
 * **[amounts] never collapses to a scalar, not even with a single currency in it.** A
 * consumer infers the shape of a response from the examples it sees; collapsing would
 * teach it the scalar form, which works for months and then breaks on the day the user
 * opens an account in another currency. Only a read scoped to a single account — which
 * declares one currency by construction — answers a bare [MoneyAmount].
 */
@Serializable
data class MoneyByCurrencyPayload(
    /**
     * One amount per currency the figure is denominated in, in currency-code order. A
     * collection, always — see the class note.
     */
    val amounts: List<MoneyAmount>,
    /**
     * The same figure reduced to the base currency, **beside** [amounts] and never
     * instead of it, or the reason there is no such number.
     *
     * `null` only where a surface did not ask for a consolidation at all.
     */
    val consolidated: ConsolidatedMoney? = null,
)

/** Why a figure could not be expressed as one number. */
enum class ConsolidationGap(val message: String) {
    /**
     * Some currency of the figure has no rate on file for the reference date. The figure
     * is complete all the same — it is [MoneyByCurrencyPayload.amounts] — and only its
     * reduction to one number is missing.
     */
    MISSING_RATE("No exchange rate on file for at least one currency of this figure at the reference date"),
}

/**
 * The consolidated value, with the provenance that makes it reproducible — or its
 * declared absence.
 *
 * **A missing rate never becomes a number.** Not a rate of one, not today's quote
 * standing in for the dated one, and never by dropping the currency nobody could price.
 * A total that is absent is reported as absent; a total that is wrong is reported as
 * true, because nothing in the response lets anyone suspect it.
 */
@Serializable
sealed interface ConsolidatedMoney {

    /**
     * The figure as one number, together with every rate that produced it.
     *
     * A consolidated number without its rates is irreproducible, and an agent will do
     * arithmetic on top of it regardless.
     */
    @Serializable
    @SerialName("available")
    data class Available(
        val amount: MoneyAmount,
        /**
         * The date whose rates produced this number, or `null` when no rate took part —
         * a single-currency figure is exact in its own currency and converts nothing
         * (the reducer's own rule).
         */
        val asOf: LocalDate? = null,
        /** Every rate applied, one per currency that was not already the base. */
        val appliedRates: List<AppliedRate> = emptyList(),
        /**
         * Whether any applied rate is an observation **older** than the date it was
         * applied to. Derived from [appliedRates] and checked against it, so the two
         * cannot disagree.
         */
        val isStale: Boolean = false,
    ) : ConsolidatedMoney {
        init {
            require(isStale == appliedRates.any { it.isStale }) {
                "isStale must be exactly `appliedRates.any { it.isStale }`"
            }
        }
    }

    /** There is no consolidated number, and this is why. */
    @Serializable
    @SerialName("unavailable")
    data class Unavailable(
        val reason: ConsolidationGap,
        /** English, for a log — the currencies that could not be priced are named here. */
        val message: String,
    ) : ConsolidatedMoney
}

/** One rate as it was applied: both of its ends, its value, the day it is about. */
@Serializable
data class AppliedRate(
    /** ISO 4217 code of the currency being priced. */
    val currency: String,
    /** ISO 4217 code of the currency [currency] is priced in. */
    val counterCurrency: String,
    /** Units of [counterCurrency] per one unit of [currency]. */
    val rate: Double,
    /** The day this rate is an observation about. */
    val date: LocalDate,
    /**
     * Whether the observation predates the date it was applied to. The archive answers
     * with the last rate **on or before** the date asked for, so this is how a consumer
     * learns that a figure about March was priced with February's quote.
     */
    val isStale: Boolean,
)

/**
 * The one place this surface turns a ledger figure into a payload.
 *
 * The consolidated value comes from [ConsolidateMoneyUseCase] — the only place in the app
 * where a rate multiplies anything — and the MCP boundary gets no exception for being
 * serialisable. **Nothing here converts, and nothing here reads the base currency**: what
 * the reduction did is read off the reduction itself, and the archive is consulted only
 * for the *provenance* of rates the reducer has already applied. The number and the rates
 * that produced it therefore cannot disagree.
 */
class MoneyPayloadFactory(
    private val consolidateMoney: ConsolidateMoneyUseCase,
    private val exchangeRates: IExchangeRateRepository,
) {

    /**
     * The scalar form, for a read **scoped to a single account** — the only read allowed
     * to be scalar, because the account declares one currency and it cannot change.
     *
     * @param value the amount in the major unit, as the ledger answered it.
     */
    fun scoped(value: Double, currency: String, sign: DisplaySign): MoneyAmount =
        MoneyAmount.of(sign * value, currency)

    /**
     * The collection form, for a read that can span accounts.
     *
     * @param on the date whose rates apply. A figure about March is consolidated at
     * March's rates, or the past would move on its own whenever a rate changed.
     */
    suspend fun spanning(
        money: MoneyByCurrency,
        sign: DisplaySign,
        on: LocalDate,
    ): MoneyByCurrencyPayload {
        val signed = MoneyByCurrency.of(
            money.toList().associate { it.currency to sign * it.value },
        )

        return MoneyByCurrencyPayload(
            amounts = signed.toList().map { MoneyAmount.of(it.value, it.currency) },
            consolidated = consolidate(signed, on),
        )
    }

    private suspend fun consolidate(signed: MoneyByCurrency, on: LocalDate): ConsolidatedMoney {
        val figure = consolidateMoney(signed, on) { value, currency, isApproximate ->
            DisplayAmount.natural(value, currency, isApproximate)
        }

        // **A figure of several terms is a figure no rate could reduce**, and that is the
        // reducer's own vocabulary: everything it could convert lands in the base term,
        // and every term beside it is a currency the archive says nothing about. So the
        // gap is read off the reduction rather than guessed at beforehand — which is also
        // what keeps this from having to know which currency the base is.
        if (!figure.isSingleTerm) {
            val unpriced = (figure.terms.map { it.currency } - setOfNotNull(figure.base?.currency)).sorted()
            return ConsolidatedMoney.Unavailable(
                reason = ConsolidationGap.MISSING_RATE,
                message = "${ConsolidationGap.MISSING_RATE.message}: ${unpriced.joinToString()}",
            )
        }

        val amount = figure.terms.map { MoneyAmount.of(it.value, it.currency) }.single()

        // The reducer reports a date exactly when a rate **converted** something, and a
        // single-currency figure converts nothing: it is delivered exact, in its own
        // currency, base or not. Listing rates there would name rates that were never
        // applied — so the provenance follows what the reduction actually did.
        //
        // A term of exactly zero is not a share of the figure — it is a currency the user
        // happens to hold an account in, sitting empty — and the reducer drops it before
        // reading any rate, so naming its rate here would name one more that was not used.
        val applied = if (figure.asOf == null) emptyList() else {
            val rates = exchangeRates.ratesAsOf(on)
            signed.toList()
                .filter { it.value != 0.0 && it.currency != amount.currency }
                .map { it.currency }
                .distinct()
                .sorted()
                .mapNotNull { currency ->
                    rates[currency]?.let { rate ->
                        AppliedRate(
                            currency = rate.currency,
                            counterCurrency = rate.counterCurrency,
                            rate = rate.rate,
                            date = rate.date,
                            isStale = rate.date < on,
                        )
                    }
                }
        }

        return ConsolidatedMoney.Available(
            amount = amount,
            asOf = figure.asOf,
            appliedRates = applied,
            isStale = applied.any { it.isStale },
        )
    }
}
