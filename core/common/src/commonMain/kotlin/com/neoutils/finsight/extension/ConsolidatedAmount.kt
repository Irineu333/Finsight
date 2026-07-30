package com.neoutils.finsight.extension

import androidx.compose.runtime.Immutable
import kotlinx.datetime.LocalDate

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
    val terms: List<DisplayAmount>,
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
    /**
     * The date whose rates produced this figure, or `null` when none did.
     *
     * It is here, and not passed to the footer by each screen, for the same reason the
     * mark is: whoever produced the figure knows it, and a screen that had to supply it
     * would be deciding something it does not know. Non-null exactly when
     * [isApproximate] is true — the reference date of a figure nothing was converted for
     * is not a fact about the figure.
     */
    val asOf: LocalDate? = null,
) {
    /** The term in the base currency, when the figure has one. */
    val base: DisplayAmount? get() = baseIndex?.let(terms::getOrNull)

    val isSingleTerm: Boolean get() = terms.size == 1
}

/**
 * The text of each term of a figure, in the order they are read.
 *
 * **One figure carries one mark.** The first term is formatted the ordinary way, so an
 * approximate figure gets its `≈` from the same place that resolves the sign (design
 * D21); every term after it suppresses the repetition and instead carries the `+` that
 * joins it to the one above — an operator of juxtaposition, not of addition, which is why
 * it sits glued to the term (design D22).
 *
 * The rule lives beside the formatter and returns text, not layout: how the terms are
 * *stacked* is the single layout rule in `:core:designsystem`, and how they *read* is
 * here, where every other money string in the app is decided.
 */
fun CurrencyFormatter.formatTerms(figure: ConsolidatedAmount): List<String> =
    figure.terms.mapIndexed { index, term ->
        if (index == 0) format(term) else "+${format(term, withMark = false)}"
    }

/**
 * The single term a surface too narrow for the whole figure shows — design D20's declared
 * degradation. It is the term in the base currency, because that is the one everything
 * that could be reduced was reduced *into*; when the figure has no base term at all (two
 * foreign currencies, no rate) it is the first, which by construction still carries the
 * figure's mark.
 *
 * The caller still has to say that a term was left out. Dropping it silently is exactly
 * the failure the approximation mark exists to prevent.
 */
fun ConsolidatedAmount.degradedTerm(): DisplayAmount = base ?: terms.first()

/**
 * The date to explain, when a surface holds several figures and at least one of them is
 * approximate — `null` when every figure on it is exact.
 *
 * It is the rule behind the footer of design D21, kept here rather than in the component
 * so that "the footer does not appear when nothing was converted" is a fact a test can
 * state, and so that no screen decides it.
 */
fun List<ConsolidatedAmount>.approximationDate(): LocalDate? =
    firstOrNull { it.isApproximate }?.asOf
