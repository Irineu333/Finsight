package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

/**
 * A monetary figure together with everything without which it cannot be read: the policy
 * by which its sign is read, the **currency** it is denominated in, and whether it is
 * **exact or approximate**. The four are indissociable on purpose. A value carried
 * without one of them is the failure mode this type exists to make impossible: the
 * figure changes, the flag that was supposed to follow it does not, and the amount
 * renders right with the wrong sign — or with the wrong symbol, which is worse, because
 * "R$ 830,00" over a dollar balance is an entirely plausible sentence.
 *
 * It answers *how a figure reads*, never *how much it is worth*: what it may not do is
 * **calculate** — combine two values, convert, add —, which belongs to the ledger and to
 * the consolidation layer. Carrying the denomination of a single number is not
 * calculation; it is the caption without which the number does not read. A policy may
 * transform its own value for reading — magnitude, negation, a floor at zero — which is
 * presentation of a single number rather than arithmetic.
 *
 * [value] carries the sign as displayed, so whatever decides by sign — color, tone,
 * ordering — reads it from the same source as the text.
 *
 * [isApproximate] is **derived** by whoever produced the figure — the reducer in
 * `:core:model` — and never declared by a screen. It travels here so that it cannot be
 * lost on the way to the surface: a value that drops its mark is indistinguishable from
 * an exact one, and the failure is silent.
 *
 * Build one through the named constructors in [Companion]; render it with
 * `CurrencyFormatter.format(DisplayAmount)`.
 */
@Immutable
class DisplayAmount private constructor(
    val value: Double,
    val policy: SignPolicy,
    val currency: String,
    val isApproximate: Boolean,
) {

    /**
     * The closed set of ways a figure reads. [NEUTRAL] and [NATURAL] behave alike today
     * and mean different things: a line that means "this moves nothing here" must stay
     * signless if its value ever turns negative, which is only decidable if the intent
     * was recorded.
     */
    enum class SignPolicy {
        /** Magnitude, no sign — the label already gives the direction. */
        MAGNITUDE,

        /** The figure carries its own sign, and only a negative one is visible. */
        NATURAL,

        /** No sign at all — both legs sit inside this perimeter, so it moves nothing here. */
        NEUTRAL,

        /** Always `+` or `-` — the direction is not derivable from the label. */
        EXPLICIT_SIGN,

        /** `+` over the magnitude — a line that always adds to the sum above it. */
        FORCED_POSITIVE,

        /** `-` over the magnitude — a line that always subtracts from it. */
        FORCED_NEGATIVE,

        /**
         * How much is owed, from a balance in the ledger's sign: a liability you owe on is
         * stored negative, and the line answers the magnitude of the debt. A card in credit
         * owes nothing, so it reads zero rather than a negative debt.
         */
        OWED,
    }

    override fun equals(other: Any?) = other is DisplayAmount &&
            value == other.value &&
            policy == other.policy &&
            currency == other.currency &&
            isApproximate == other.isApproximate

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + policy.hashCode()
        result = 31 * result + currency.hashCode()
        result = 31 * result + isApproximate.hashCode()
        return result
    }

    override fun toString() =
        "DisplayAmount($value, $policy, $currency, approximate=$isApproximate)"

    /**
     * Every constructor takes [currency] and [isApproximate] **without a default**. A
     * default would be exactly the escape hatch the type exists to close: a site that
     * forgot the currency would compile and render the device locale's symbol over
     * someone else's money, and a site that forgot the mark would render an approximate
     * figure as an exact one.
     */
    companion object {
        /** Magnitude, no sign — the label already gives the direction. */
        fun magnitude(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(value.absoluteValue, SignPolicy.MAGNITUDE, currency, isApproximate)

        /** A balance: only the negative is information. */
        fun natural(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(value, SignPolicy.NATURAL, currency, isApproximate)

        /** Moves nothing in this perspective, whatever its sign. */
        fun neutral(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(value, SignPolicy.NEUTRAL, currency, isApproximate)

        /** Spelled out in both directions, because the label gives neither. */
        fun explicitSign(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(value, SignPolicy.EXPLICIT_SIGN, currency, isApproximate)

        /** Always adds: the magnitude, read as positive. */
        fun forcedPositive(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(
                value.absoluteValue,
                SignPolicy.FORCED_POSITIVE,
                currency,
                isApproximate
            )

        /** Always subtracts: the magnitude, read as negative. */
        fun forcedNegative(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(
                -value.absoluteValue,
                SignPolicy.FORCED_NEGATIVE,
                currency,
                isApproximate
            )

        /** The debt behind a balance in the ledger's sign; zero when nothing is owed. */
        fun owed(value: Double, currency: String, isApproximate: Boolean) =
            DisplayAmount(maxOf(0.0, -value), SignPolicy.OWED, currency, isApproximate)
    }
}

/** The mark of an approximation, spelled rather than coloured — see [format]. */
const val APPROXIMATION_MARK = "≈"

/**
 * What a figure reads as when it cannot be resolved **at all** — see [formatOrUnresolved].
 *
 * Distinct from the approximation mark, and the distinction is the whole point. `≈ R$ 375,00`
 * says "this number is close"; this says "there is no number", which is a different claim and
 * the honest one when part of the money sits in a currency no rate reaches. What it replaces
 * is a zero, or a floor dressed as a total — both of which read as facts.
 *
 * It occupies the width of an amount and nothing more, so a surface that shows it keeps its
 * shape. That is deliberate: the alternative to a wrong number is not a broken layout.
 */
const val UNRESOLVED_AMOUNT = "***"

/**
 * Renders a [DisplayAmount]: the amount in **its own** currency, the sign by its policy,
 * and the approximation mark when it carries one.
 *
 * The policies that spell a sign out concatenate it over the formatted magnitude, which
 * is what every site in the app does today — so absorbing them is a demonstrable no-op
 * on the text. Letting `NumberFormat` place the negative itself would be more correct,
 * and is a decision of its own.
 *
 * **The mark is resolved here, and is always more external than the sign** — `≈ +R$
 * 1.240,00` (design D21). Outermost is the only position that survives a locale putting
 * the symbol on the right, and resolving it in the one place that already resolves the
 * sign is what makes the same rule produce the same text on every surface, the colourless
 * exported document included. Colour is ruled out: the palette is entirely taken by
 * transaction nature, and the amber "approximate" would want is literally `Adjustment`.
 *
 * @param withMark `false` only for a term of a multi-term figure that is not the first:
 * the mark scopes the **figure**, and one figure carries one mark. That call belongs to
 * the single multi-term renderer in `:core:designsystem`, never to a screen.
 *
 * It lives here, as an extension, rather than on the `expect class`: the rule is the
 * same on every platform, and only the locale is not.
 */
fun CurrencyFormatter.format(amount: DisplayAmount, withMark: Boolean = true): String {
    val currency = amount.currency

    val signed = when (amount.policy) {
        DisplayAmount.SignPolicy.MAGNITUDE,
        DisplayAmount.SignPolicy.NATURAL,
        DisplayAmount.SignPolicy.NEUTRAL,
        DisplayAmount.SignPolicy.OWED -> format(amount.value, currency)

        DisplayAmount.SignPolicy.FORCED_POSITIVE ->
            "+${format(amount.value.absoluteValue, currency)}"

        DisplayAmount.SignPolicy.FORCED_NEGATIVE ->
            "-${format(amount.value.absoluteValue, currency)}"

        DisplayAmount.SignPolicy.EXPLICIT_SIGN -> when {
            amount.value > 0 -> "+${format(amount.value.absoluteValue, currency)}"
            amount.value < 0 -> "-${format(amount.value.absoluteValue, currency)}"
            else -> format(amount.value.absoluteValue, currency)
        }
    }

    return if (amount.isApproximate && withMark) "$APPROXIMATION_MARK $signed" else signed
}

/**
 * The same rendering for a figure that **may not exist**: [UNRESOLVED_AMOUNT] when it does
 * not, the amount when it does.
 *
 * `null` is how a producer says "there is no number here" — the same vocabulary the app
 * already uses for a share that cannot be taken and a category with no measurable
 * magnitude. Having one place decide what that looks like is what keeps three screens
 * showing a budget from each inventing their own answer, which is how one of them ended up
 * printing a confident `R$ 0,00` over spending it could not price.
 */
fun CurrencyFormatter.formatOrUnresolved(amount: DisplayAmount?): String =
    amount?.let { format(it) } ?: UNRESOLVED_AMOUNT
