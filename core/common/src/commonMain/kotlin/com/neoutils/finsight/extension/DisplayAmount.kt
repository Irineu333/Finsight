package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable
import kotlin.math.absoluteValue

/**
 * A monetary figure together with the policy by which its sign is read and the
 * [Denomination] it is expressed in — the three are indissociable on purpose. A value
 * carried without its policy is the failure mode this type exists to make impossible:
 * the figure changes, the flag that was supposed to follow it does not, and the amount
 * renders right with the wrong sign. The currency is the same failure mode, and worse,
 * because "R$ 830,00" over a dollar balance is an entirely plausible sentence.
 *
 * It answers *how a figure reads*, never *how much it is worth*: it combines no two
 * values and converts between no two currencies, both of which belong above it — the
 * ledger owns arithmetic, the consolidation layer owns conversion. Carrying the
 * denomination of a single number is neither: it is the legend without which the number
 * cannot be read. A policy may transform its own value for reading — magnitude,
 * negation, a floor at zero — which is presentation of a single number rather than
 * arithmetic.
 *
 * [value] carries the sign as displayed, so whatever decides by sign — color, tone,
 * ordering — reads it from the same source as the text.
 *
 * A consolidated figure may need **more than one** of these — one term per currency that
 * could not be reduced — and the surface renders them stacked. Juxtaposing terms is not
 * combining values: it is the refusal to add what does not add, expressed as layout.
 *
 * Build one through the named constructors in [Companion]; render it with
 * `CurrencyFormatter.format(DisplayAmount)`.
 */
@Immutable
class DisplayAmount private constructor(
    val value: Double,
    val policy: SignPolicy,
    val denomination: Denomination,
) {

    val currency: String get() = denomination.currency

    val isApproximate: Boolean get() = denomination.isApproximate

    /**
     * Whether this figure's own reading already opens with a `+` or a `-`.
     *
     * It is derived from the policy and the value, which is the only place both are known,
     * and it exists for the one caller that has to *not* add a sign: juxtaposing the terms
     * of a [MoneyFigure] glues a `+` to each term after the first, and a term that already
     * spells its own direction would read `++US$ 50,00`. Where the term spells one, that
     * sign **is** the juxtaposition operator.
     */
    val spellsOwnSign: Boolean
        get() = when (policy) {
            SignPolicy.FORCED_POSITIVE, SignPolicy.FORCED_NEGATIVE -> true
            SignPolicy.EXPLICIT_SIGN -> value != 0.0
            SignPolicy.NATURAL -> value < 0
            SignPolicy.MAGNITUDE, SignPolicy.NEUTRAL, SignPolicy.OWED -> false
        }

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
            denomination == other.denomination

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + policy.hashCode()
        result = 31 * result + denomination.hashCode()
        return result
    }

    override fun toString() = "DisplayAmount($value, $policy, $denomination)"

    companion object {
        /** Magnitude, no sign — the label already gives the direction. */
        fun magnitude(value: Double, denomination: Denomination) =
            DisplayAmount(value.absoluteValue, SignPolicy.MAGNITUDE, denomination)

        /** A balance: only the negative is information. */
        fun natural(value: Double, denomination: Denomination) =
            DisplayAmount(value, SignPolicy.NATURAL, denomination)

        /** Moves nothing in this perspective, whatever its sign. */
        fun neutral(value: Double, denomination: Denomination) =
            DisplayAmount(value, SignPolicy.NEUTRAL, denomination)

        /** Spelled out in both directions, because the label gives neither. */
        fun explicitSign(value: Double, denomination: Denomination) =
            DisplayAmount(value, SignPolicy.EXPLICIT_SIGN, denomination)

        /** Always adds: the magnitude, read as positive. */
        fun forcedPositive(value: Double, denomination: Denomination) =
            DisplayAmount(value.absoluteValue, SignPolicy.FORCED_POSITIVE, denomination)

        /** Always subtracts: the magnitude, read as negative. */
        fun forcedNegative(value: Double, denomination: Denomination) =
            DisplayAmount(-value.absoluteValue, SignPolicy.FORCED_NEGATIVE, denomination)

        /** The debt behind a balance in the ledger's sign; zero when nothing is owed. */
        fun owed(value: Double, denomination: Denomination) =
            DisplayAmount(maxOf(0.0, -value), SignPolicy.OWED, denomination)
    }
}

/**
 * Renders a [DisplayAmount] in its own currency, formatted for the formatter's locale.
 *
 * The policies that spell a sign out concatenate it over the formatted magnitude, which
 * is what every site in the app does today — so absorbing them is a demonstrable no-op
 * on the text. Letting `NumberFormat` place the negative itself would be more correct,
 * and is a decision of its own.
 *
 * The approximation mark goes through the same door, and sits **outside** the sign
 * (`≈ +R$ 1.240,00`): it qualifies the whole figure, not its direction, and outermost is
 * the only position that survives a locale placing the symbol on the right. It is inert
 * until something is actually approximate, which only the consolidation layer produces.
 *
 * It lives here, as an extension, rather than on the `expect class`: the rule is the
 * same on every platform, and only the locale is not.
 */
fun CurrencyFormatter.format(amount: DisplayAmount): String {
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

    return if (amount.isApproximate) approximated(signed) else signed
}

/**
 * [text] marked as approximate.
 *
 * The mark lives here, beside the sign, because it goes through the same door and must come
 * out the same in every surface — including the ones without colour. It is a prefix, and the
 * outermost one: it qualifies the whole figure rather than its direction, and outermost is the
 * only position that survives a locale placing the symbol on the right.
 */
internal fun approximated(text: String) = "≈ $text"
