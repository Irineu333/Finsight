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
