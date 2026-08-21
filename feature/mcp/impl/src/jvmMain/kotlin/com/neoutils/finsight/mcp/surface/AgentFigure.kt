package com.neoutils.finsight.mcp.surface

import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A monetary figure as an agent receives it: the number, the currency it is expressed in, and
 * everything without which the number cannot be repeated to a person.
 *
 * **A number with no currency is the one payload this surface must never produce.** The ledger
 * answers per currency, and `{"amount": 1234.56}` throws that away — the agent then adds reais to
 * dollars in its next sentence and nothing anywhere disagrees with it. So [byCurrency] is always
 * present and always the ledger's own exact amounts, whatever [amount] managed to say.
 *
 * **[amount] is nullable, and that is the honest part.** A figure spanning currencies is one number
 * only as far as the rate archive allows; where no rate reaches it, there is no single number and
 * the surface says so through [limitation] rather than picking one (design D16). Reducing is
 * conversion, conversion has exactly one owner in the app, and this type is built by consuming it —
 * see `ConsolidateMoneyUseCase.agentFigure`.
 */
@Serializable
internal data class AgentFigure(
    /**
     * The figure as one number, or `null` when it has none — several currencies and no rate that
     * reaches any of them. When some part converted and some did not, this is the converted part
     * and [limitation] says what it leaves out.
     */
    val amount: Double?,
    /** The currency [amount] is expressed in. `null` exactly when [amount] is. */
    val currency: String?,
    /**
     * The figure decomposed by currency, exact, as the ledger answered it. A currency present with
     * a zero is not noise: it says there is movement in that currency and it nets to zero, which is
     * a different fact from the currency being absent.
     */
    @SerialName("by_currency")
    val byCurrency: List<AgentMoney>,
    /**
     * Whether the figure is approximate — a rate multiplied something on the way here, **or** it
     * holds parts that do not add up. Wider than [rateDate] on purpose: the two are different
     * facts, and collapsing them lets a payload name a rate that was never applied.
     */
    @SerialName("is_approximate")
    val isApproximate: Boolean,
    /** The date whose rates produced [amount], or `null` when no rate took part. */
    @SerialName("rate_date")
    val rateDate: LocalDate? = null,
    /** What the figure could not do, and why. `null` when nothing was missing. */
    val limitation: AgentFigureLimitation? = null,
) {
    companion object {

        /**
         * A figure that never crossed a currency: a single account's balance, one leg of a
         * transaction, an amount the user typed. Exact, denominated in its own currency, and
         * never in the base — the base is a display preference, not a resort for a figure whose
         * own currency is knowable.
         */
        fun exact(amount: Double, currency: String) = AgentFigure(
            amount = amount,
            currency = currency,
            byCurrency = listOf(AgentMoney(currency = currency, amount = amount)),
            isApproximate = false,
        )
    }
}

/** One denominated term of a figure. */
@Serializable
internal data class AgentMoney(
    val currency: String,
    val amount: Double,
)

/**
 * What a figure could not be reduced to, and why — the response D16 requires when the local rate
 * archive has nothing to convert a part of it with.
 *
 * It exists so the agent reports the limitation instead of the alternatives: omitting the currency
 * it could not price, or handing over an approximation as though it were exact. Both read as a
 * complete answer, and neither is one.
 */
@Serializable
internal data class AgentFigureLimitation(
    /** The currencies no rate in the archive reached on the reference date. */
    @SerialName("missing_rate_for")
    val missingRateFor: List<String>,
    /** The limitation in words, so whatever relays the figure can relay this with it. */
    val explanation: String,
)
