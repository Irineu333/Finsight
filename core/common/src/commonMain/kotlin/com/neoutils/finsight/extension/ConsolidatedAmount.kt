package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable

/**
 * A figure the user reads as one thing, which in the general case is a **list of
 * terms** — almost always a single one.
 *
 * A consolidation reduces a per-currency balance to the base currency *as far as the
 * known rates allow*; what cannot be reduced stays as a term of its own. So
 * `R$ 100,00 + US$ 50,00` is not a sum that failed: it is the refusal to add what does
 * not add, expressed as layout. Nothing is invented and nothing is dropped — a missing
 * rate never becomes `1`, never disappears from the total and never blanks the screen.
 *
 * **The exactness belongs to the figure, not to a term.** Two exact terms placed side
 * by side still form an approximate figure, because a conversion happened somewhere in
 * getting there. [isApproximate] has no default for the same reason `DisplayAmount`
 * demands a sign policy: a figure carried without it is the failure this type exists to
 * make impossible.
 *
 * **Only the reducer builds one.** The type carries; the use case in `:core:model`
 * calculates. That is what keeps `DisplayAmount`'s ban on combining two values intact
 * while still letting a screen show the result of a combination.
 *
 * **Why it lives here and not with the rest of the consolidation.** The multi-term
 * renderer is a single layout rule and therefore belongs to `:core:designsystem`, whose
 * build sees only `core/common` and `core/resources`. Putting the figure in
 * `:core:model` would make that single rule unrealisable, or force a
 * `designsystem → model` edge — the wrong inversion.
 */
@Immutable
data class ConsolidatedAmount(
    val terms: List<Term>,
    val isApproximate: Boolean,
    /**
     * Which term is denominated in the base currency, if any.
     *
     * An index rather than a second copy of the term, so the figure cannot disagree
     * with itself. A surface too narrow for more than one term degrades to *this* one
     * and says that it did (design D20) — and "the first one" is not a promise a
     * positional list can keep.
     */
    val baseIndex: Int? = null,
) {
    /** The term in the base currency, when the figure has one. */
    val base: Term? get() = baseIndex?.let(terms::getOrNull)

    val isSingleTerm: Boolean get() = terms.size == 1

    /**
     * One term of a figure: an amount and what it is denominated in.
     *
     * Declared scaffolding, and it says so: design D10 has the currency travelling
     * *inside* `DisplayAmount`, indissociable from the value, and it will — task 7.1
     * puts it there along with every one of the 133 sites that build one. Until that
     * lands, the currency rides alongside so that this type can exist at all, and
     * folding it in is a mechanical substitution that deletes this class.
     */
    @Immutable
    data class Term(val amount: DisplayAmount, val currency: String)
}
