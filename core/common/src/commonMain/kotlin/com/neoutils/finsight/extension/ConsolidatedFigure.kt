package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable

/**
 * The result of reducing a per-currency figure: what it **reads** as, and the one number a
 * caller may order or fraction it by.
 *
 * The two are projections of a single decision, and that is why they travel together. A
 * consumer that kept the number in one field and rebuilt the figure elsewhere would take a
 * second decision about which quote governs it, and two decisions diverge — the number
 * reduced at one rate, the figure explaining itself with another.
 *
 * [comparable] is for arithmetic that presentation legitimately needs — ranking a list of
 * categories, dividing spending by a limit, choosing a colour by sign — and **never** for
 * text. It is the **leading term as it reads**, sign policy already applied, so the number and
 * the text can never disagree about direction. Rendering goes through [figure], which is the
 * only thing that says how much of the whole is being shown.
 *
 * [isPartial] is why this is a type and not a pair. The reduced number leaves out every term
 * no rate reached, so a share or a fraction computed over it is a *declared* approximation
 * rather than an implicit one, and the flag travels wherever the number does.
 *
 * Only the consolidation layer builds one — it is the single owner of reducing a
 * per-currency result, and it knows both projections before either exists as a figure.
 */
@Immutable
class ConsolidatedFigure(
    val figure: MoneyFigure,
    val comparable: Double,
    val isPartial: Boolean,
) {

    /**
     * Whether anything about this figure is less than exact — a conversion took part, or the
     * reduced number left a term out. It is what a fraction derived from [comparable] has to
     * carry, and it is asked here rather than of the figure so no caller reconciles the two
     * halves on its own.
     */
    val isApproximate: Boolean get() = figure.isApproximate || isPartial

    override fun equals(other: Any?) = other is ConsolidatedFigure &&
            figure == other.figure &&
            comparable == other.comparable &&
            isPartial == other.isPartial

    override fun hashCode(): Int {
        var result = figure.hashCode()
        result = 31 * result + comparable.hashCode()
        result = 31 * result + isPartial.hashCode()
        return result
    }

    override fun toString() = "ConsolidatedFigure($figure, comparable=$comparable, partial=$isPartial)"
}
