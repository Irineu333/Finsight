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
     * would be deciding something it does not know.
     *
     * Non-null exactly when a rate **converted** something — which is narrower than
     * [isApproximate]. A figure of several currencies that no rate could touch is
     * approximate (it holds amounts that do not add up) and yet has no reference date,
     * because no rate took part in it. The two are different facts, and conflating them
     * is what lets a surface name a rate that was never applied.
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
 * **The joining `+` is not added to a term that already shows a sign.** A summary line is
 * a signed figure — income reads `+`, expense reads `-` — and gluing the joiner onto it
 * produced `++R$ 100,00` and `+-US$ 50,00` on the statement and the exported report. A
 * term that spells its own sign already reads as a continuation of the one above it, and
 * the joiner has nothing left to do. It is decided from the term's **policy and value**,
 * never from the text it produced: a locale is free to place the minus wherever it likes,
 * and this rule cannot depend on where.
 *
 * The rule lives beside the formatter and returns text, not layout: how the terms are
 * *stacked* is the single layout rule in `:core:designsystem`, and how they *read* is
 * here, where every other money string in the app is decided.
 */
fun CurrencyFormatter.formatTerms(figure: ConsolidatedAmount): List<String> =
    figureTerms(figure).map { it.text }

/**
 * The same terms, with the joiner **kept apart from the amount** — for the one renderer
 * that stacks them (design D22), which needs to place the two rather than print them.
 *
 * Stacked and left-aligned, the joiner reads as a bullet of a list and wants the room a
 * bullet has; right-aligned it is glued to the amount, because there the column edge is
 * what says "same figure". Both are layout, and layout has one owner — but it cannot own
 * this while the joiner is already baked into a string.
 */
fun CurrencyFormatter.figureTerms(figure: ConsolidatedAmount): List<MoneyTermText> =
    figure.terms.mapIndexed { index, term ->
        MoneyTermText(
            // The mark belongs to the term a rate passed through, and to no other. A
            // term no rate touched is the very amount the ledger answered — exact, in
            // its own currency — and marking it would claim uncertainty about a number
            // the app knows perfectly well.
            mark = APPROXIMATION_MARK.takeIf { term.isApproximate },
            joiner = "+".takeIf { index > 0 && !term.spellsItsOwnSign },
            amount = format(term, withMark = false),
        )
    }

/**
 * One term of a figure as text, in the three parts a surface may place: the mark, when
 * this term is the one a rate passed through; what joins it to the term above, when
 * anything does; and the amount itself.
 *
 * They are kept apart because their **order is a rule**: the mark is always more external
 * than the sign and than the joiner (design D21), and a renderer that received them
 * already concatenated could not honour that and place them at the same time.
 *
 * [joiner] is `null` for the first term — nothing precedes it — and for any term that
 * spells its own sign, which already reads as a continuation.
 */
data class MoneyTermText(
    val mark: String?,
    val joiner: String?,
    val amount: String,
) {
    /** The three parts as one string, in the order they are read. */
    val text: String get() = listOfNotNull(mark?.plus(" "), joiner, amount).joinToString("")
}

/**
 * Whether this term already begins with a sign of its own — the fact [formatTerms] needs
 * so that a joiner is never stacked on top of one.
 */
private val DisplayAmount.spellsItsOwnSign: Boolean
    get() = when (policy) {
        DisplayAmount.SignPolicy.FORCED_POSITIVE,
        DisplayAmount.SignPolicy.FORCED_NEGATIVE -> true

        DisplayAmount.SignPolicy.EXPLICIT_SIGN -> value != 0.0

        // These print the value as it is, so only a negative one shows a sign.
        DisplayAmount.SignPolicy.NATURAL,
        DisplayAmount.SignPolicy.NEUTRAL -> value < 0.0

        // Always a magnitude: never signed.
        DisplayAmount.SignPolicy.MAGNITUDE,
        DisplayAmount.SignPolicy.OWED -> false
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
 * The figure a surface has to explain, when it holds several and at least one of them is
 * approximate — `null` when every figure on it is exact.
 *
 * It is the rule behind the badge of design D21, kept here rather than in the component
 * so that "the badge does not appear when nothing was converted" is a fact a test can
 * state, and so that no screen decides it. The whole figure and not just its date,
 * because what has to be explained is *what the reduction did*: a figure of several
 * currencies with no rate at all converted nothing, and naming a rate there would name
 * one that was never applied.
 */
fun List<ConsolidatedAmount>.approximateFigure(): ConsolidatedAmount? =
    firstOrNull { it.isApproximate }

/**
 * **How badly consolidation is affecting what a surface shows** — three states, because a
 * user needs to tell them apart at a glance and the app had been saying all three the same
 * way.
 *
 * They are ordered by how much the surface has lost, and the order is the whole point: a
 * card at one level is not also at the levels below it, so the highest one it reaches is
 * the one it reports.
 *
 * Deriving this here, beside the figure, is what keeps a screen from deciding it — the
 * same reason [approximateFigure] lives here. A surface that judged its own severity would
 * be a surface free to under-report it, and under-reporting is the direction that matters.
 */
enum class ConsolidationNotice {
    /**
     * **A rate was applied and the number is still one number.** Nothing was lost: the
     * figure reads as a single amount, only an approximate one, and the `≈` on it already
     * says so. The notice is a courtesy — where the value came from, and as of when — so it
     * is the quiet one.
     */
    CONVERTED,

    /**
     * **The figure could not be reduced to one number, and its parts are stacked.** Some
     * currency on this surface has no rate, so `R$ 100,00 + US$ 50,00` is what there is.
     * Every amount is still exact and nothing is hidden — but the user is now reading a
     * layout where they expected a total, and one registered rate would collapse it. That
     * is a state to act on, not merely to know about.
     */
    STACKED,

    /**
     * **The surface needed one number and has none, so part of it is not working.** A
     * budget bar that cannot be drawn, a proportion that cannot be taken, a total replaced
     * by a placeholder. The amounts behind it are perfectly known; what is missing is any
     * common measure to put them against, and until a rate exists the surface cannot do
     * the job it is on the screen to do.
     *
     * It is not derivable from the figures alone — a blanked bar is *what is not drawn* —
     * so the surface that suppressed something is what declares it.
     */
    UNRESOLVED,
}

/**
 * The one notice a surface reports, given every figure it shows and whether it had to
 * suppress something for want of a rate.
 *
 * `null` means consolidation is not affecting this surface at all, which for a
 * single-currency user is every surface in the app — and is why no badge appears for them
 * without any screen having to check.
 *
 * @param unresolved whether this surface left something out because no single number could
 * be had: a bar not drawn, a share not taken, a total shown as a placeholder. It cannot be
 * read off the figures, so it is declared.
 */
fun List<ConsolidatedAmount>.consolidationNotice(
    unresolved: Boolean = false,
): ConsolidationNotice? = when {
    unresolved -> ConsolidationNotice.UNRESOLVED
    // Stacked before converted, and never both: a surface holding one plain approximate
    // total beside one it could not reduce has lost the second, and reporting the milder
    // of the two would describe the half that is fine.
    any { it.terms.size > 1 } -> ConsolidationNotice.STACKED
    any { it.isApproximate } -> ConsolidationNotice.CONVERTED
    else -> null
}
