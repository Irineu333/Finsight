package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

/**
 * A monetary figure together with the policy by which its sign is read — the two are
 * indissociable on purpose. A value carried without its policy is the failure mode this
 * type exists to make impossible: the figure changes, the flag that was supposed to
 * follow it does not, and the amount renders right with the wrong sign.
 *
 * It answers *how a figure reads*, never *how much it is worth*: it combines no two
 * values and knows no currency, both of which belong to the ledger. A policy may
 * transform its own value for reading — magnitude, negation, a floor at zero — which is
 * presentation of a single number rather than arithmetic.
 *
 * [value] carries the sign as displayed, so whatever decides by sign — color, tone,
 * ordering — reads it from the same source as the text.
 *
 * Build one through the named constructors in [Companion]; render it with
 * `CurrencyFormatter.format(DisplayAmount)`.
 */
@Immutable
class DisplayAmount private constructor(
    val value: Double,
    val policy: SignPolicy,
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
            policy == other.policy

    override fun hashCode() = 31 * value.hashCode() + policy.hashCode()

    override fun toString() = "DisplayAmount($value, $policy)"

    companion object {
        /** Magnitude, no sign — the label already gives the direction. */
        fun magnitude(value: Double) = DisplayAmount(value.absoluteValue, SignPolicy.MAGNITUDE)

        /** A balance: only the negative is information. */
        fun natural(value: Double) = DisplayAmount(value, SignPolicy.NATURAL)

        /** Moves nothing in this perspective, whatever its sign. */
        fun neutral(value: Double) = DisplayAmount(value, SignPolicy.NEUTRAL)

        /** Spelled out in both directions, because the label gives neither. */
        fun explicitSign(value: Double) = DisplayAmount(value, SignPolicy.EXPLICIT_SIGN)

        /** Always adds: the magnitude, read as positive. */
        fun forcedPositive(value: Double) =
            DisplayAmount(value.absoluteValue, SignPolicy.FORCED_POSITIVE)

        /** Always subtracts: the magnitude, read as negative. */
        fun forcedNegative(value: Double) =
            DisplayAmount(-value.absoluteValue, SignPolicy.FORCED_NEGATIVE)

        /** The debt behind a balance in the ledger's sign; zero when nothing is owed. */
        fun owed(value: Double) = DisplayAmount(maxOf(0.0, -value), SignPolicy.OWED)
    }
}

/**
 * Renders a [DisplayAmount] in the formatter's locale.
 *
 * The policies that spell a sign out concatenate it over the formatted magnitude, which
 * is what every site in the app does today — so absorbing them is a demonstrable no-op
 * on the text. Letting `NumberFormat` place the negative itself would be more correct,
 * and is a decision of its own.
 *
 * It lives here, as an extension, rather than on the `expect class`: the rule is the
 * same on every platform, and only the locale is not.
 */
fun CurrencyFormatter.format(amount: DisplayAmount): String = when (amount.policy) {
    DisplayAmount.SignPolicy.MAGNITUDE,
    DisplayAmount.SignPolicy.NATURAL,
    DisplayAmount.SignPolicy.NEUTRAL,
    DisplayAmount.SignPolicy.OWED -> format(amount.value)

    DisplayAmount.SignPolicy.FORCED_POSITIVE -> "+${format(amount.value.absoluteValue)}"
    DisplayAmount.SignPolicy.FORCED_NEGATIVE -> "-${format(amount.value.absoluteValue)}"
    DisplayAmount.SignPolicy.EXPLICIT_SIGN -> when {
        amount.value > 0 -> "+${format(amount.value.absoluteValue)}"
        amount.value < 0 -> "-${format(amount.value.absoluteValue)}"
        else -> format(amount.value.absoluteValue)
    }
}
