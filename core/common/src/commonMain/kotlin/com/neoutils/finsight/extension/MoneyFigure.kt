package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable

/**
 * A money figure as it is shown: a **sequence** of terms, almost always of one.
 *
 * More than one term happens when a figure spans currencies some rate could not reduce —
 * `R$ 100,00 + US$ 50,00`. Juxtaposing them is not combining values: it is the refusal to
 * add what does not add, expressed as layout. A rate that is missing therefore costs the
 * figure a term rather than its honesty: nothing becomes `1`, nothing is dropped from the
 * sum, and nothing empties the screen.
 *
 * The one-term case is the common one and stays as cheap to read as the number it wraps.
 * [isApproximate] belongs to the figure and not to a term: it is true when **any** term went
 * through a conversion, which is what makes a single mark over the whole figure the right
 * shape — the mark qualifies the figure, and no term of it can be quietly exact.
 *
 * Only the consolidation layer builds a figure of several terms, because only it knows what
 * a rate allowed. Everything else builds one of a single term, which is what every figure
 * the ledger returned in one currency is.
 */
@Immutable
class MoneyFigure private constructor(val terms: List<DisplayAmount>) {

    init {
        require(terms.isNotEmpty()) { "A money figure has at least one term: an empty one reads as nothing at all" }
    }

    /** The term a surface with room for one shows, and the first of a stack. */
    val primary: DisplayAmount get() = terms.first()

    /** The terms after the first — empty in the common case. */
    val rest: List<DisplayAmount> get() = terms.drop(1)

    val isSingleTerm: Boolean get() = terms.size == 1

    /**
     * Whether any conversion took part. It is asked of the figure rather than of a term
     * because the mark qualifies the whole of it: a figure with one converted term and one
     * exact term is an approximation of the total, however exact its parts read.
     */
    val isApproximate: Boolean get() = terms.any { it.isApproximate }

    /**
     * The rates that took part, in the order the terms carry them — derived from the terms
     * exactly like [isApproximate], because it answers the same question one level down:
     * the mark says *that* something was converted, this says *by what*.
     */
    val appliedRates: List<AppliedRate>
        get() = terms.flatMap { it.denomination.appliedRates }

    override fun equals(other: Any?) = other is MoneyFigure && terms == other.terms

    override fun hashCode() = terms.hashCode()

    override fun toString() = "MoneyFigure(${terms.joinToString(" + ")})"

    companion object {
        /** The common case: one number, in one currency. */
        fun of(term: DisplayAmount) = MoneyFigure(listOf(term))

        /**
         * A figure of one or more terms, in the order they are shown. Order is the caller's:
         * the consolidation layer puts the base term first, because it is the one a surface
         * with room for a single line keeps.
         */
        fun of(terms: List<DisplayAmount>) = MoneyFigure(terms)
    }
}

/**
 * The figure's terms as the lines a surface shows, in order — one line per term, and one
 * line in the common case.
 *
 * Every term after the first is juxtaposed onto the one above it, and the operator is
 * **glued** to its term: `+US$ 50,00`, never `+ US$ 50,00`. Spaced apart it would read as
 * arithmetic, and arithmetic is exactly what a second term means did *not* happen. Where a
 * term already spells its own direction ([DisplayAmount.spellsOwnSign]) that sign is the
 * operator, so an expense of two terms reads `-R$ 100,00` over `-US$ 50,00` rather than
 * growing a second sign.
 *
 * It returns text rather than drawing, so the rule is one testable function shared by the
 * composable that stacks the lines and by any surface that cannot stack (see the declared
 * degradation of a fixed-width surface).
 */
fun CurrencyFormatter.formatTerms(figure: MoneyFigure): List<String> =
    figure.terms.mapIndexed { index, term ->
        val text = format(term)
        if (index == 0 || term.spellsOwnSign) text else "+$text"
    }

/**
 * The figure as **one** line, for a surface whose width or grammar admits no more — a limit
 * meter, the label of a progress bar, an instalment counter, a cell of the exported document.
 *
 * A surface calls this to *declare* that it cannot show the whole figure. That declaration is
 * the point: the alternative is not "render everything", it is the layout deciding on its own,
 * by truncation or by wrapping, what a reader sees of an incomplete number — which is how a
 * figure starts lying without anyone having chosen it.
 *
 * What is shown is [MoneyFigure.primary] — the base term whenever one exists, and otherwise the
 * first of the terms no rate reached, since a figure can have no base term at all (every
 * currency in it unknown to the rates).
 *
 * Two things say a term was left out, and both are textual:
 * - the approximation mark, **forced** whatever the terms say. A figure of two unconverted
 *   terms is made of exact parts, and one of them alone is still not the figure — reading it
 *   as exact is precisely the silent loss the mark exists to prevent;
 * - a continuation marker glued to a `+`, the same juxtaposition operator [formatTerms] uses,
 *   with the term itself elided.
 *
 * Because of the forced mark, a surface asking "is anything here approximate?" — the card
 * footer that explains the mark — must ask `isApproximate || !isSingleTerm`, not
 * [MoneyFigure.isApproximate] alone, or the mark would appear with nothing to explain it.
 */
/**
 * Whether a card of these figures owes its reader an explanation — the one condition the
 * footer of design D25 is rendered on.
 *
 * It asks two things and not one. A converted figure carries the mark, and that is the
 * obvious half. The other is a figure of several terms on a surface that shows one line:
 * `formatSingleLine` **forces** the mark there even when every term is exact, so a card
 * asking only [MoneyFigure.isApproximate] would print a mark it never explains.
 *
 * It lives here, beside the two rules it reconciles, so no card decides it on its own —
 * and so the "no footer when everything is exact" of task 7.9 is a fact about a function
 * rather than about a pixel.
 */
fun explanationIsOwed(figures: List<MoneyFigure>): Boolean =
    figures.any { it.isApproximate || !it.isSingleTerm }

/**
 * The rates behind [figures], each one once — what the footer reveals.
 *
 * Distinct because one card holds several figures over the same currencies: opening
 * balance, income and expense of one account all convert at the same quote, and repeating
 * it three times would read as three rates.
 */
fun appliedRatesOf(figures: List<MoneyFigure>): List<AppliedRate> =
    figures.flatMap { it.appliedRates }.distinct().sortedBy { it.currency }

fun CurrencyFormatter.formatSingleLine(figure: MoneyFigure): String {
    val primary = format(figure.primary)

    if (figure.isSingleTerm) return primary

    val marked = if (figure.primary.isApproximate) primary else approximated(primary)

    return "$marked +…"
}
