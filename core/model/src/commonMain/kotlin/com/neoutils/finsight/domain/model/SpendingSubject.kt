package com.neoutils.finsight.domain.model

/**
 * What a breakdown line is *about*: a category, or the absence of one.
 *
 * The unclassified total is a value of the analytic axis, not a missing item. That is
 * what lets it be a key of the family of figures the consolidation reducer ranks and
 * divides — `comparativeMagnitudes` is declared over `K : Any`, and an absence could
 * never be one.
 *
 * It is **not** a `Category`: no id, no persisted name, no icon, nothing renameable,
 * archivable or removable. In the ledger it remains what it has always been, the
 * absence of a dimension on the nominal leg, and no account or category represents it.
 *
 * [Uncategorized] is a `data object` because a map key needs stable identity and
 * `hashCode`. Carrying no text is deliberate: the label is a string resource resolved
 * by whoever renders the line.
 */
sealed interface SpendingSubject {

    data class Categorized(val category: Category) : SpendingSubject

    data object Uncategorized : SpendingSubject
}

/**
 * Whether this transaction belongs to [subject] — the single definition of what a value of
 * the analytic axis contains, shared by whoever ranks it and whoever filters by it.
 *
 * [SpendingSubject.Uncategorized] asks for a **nominal leg that carries no dimension**, not
 * merely for `nominalDimensionId == null`. The two differ, and the difference is the whole
 * point: a transfer, a card payment and an adjustment have no nominal leg at all, so their
 * `nominalDimensionId` is null as well. They are not unclassified — they are outside the
 * axis, and no unclassified total ever contained them.
 *
 * An **orphan dimension** — a nominal leg carrying a dimension that resolves to no category —
 * needs no branch of its own: it has a dimension, so it is not [SpendingSubject.Uncategorized],
 * and no existing category holds that `dimensionId`, so it is no [SpendingSubject.Categorized]
 * either. It falls outside every value of the axis, which is what an integrity failure should
 * do.
 */
fun Transaction.matches(subject: SpendingSubject): Boolean = when (subject) {
    is SpendingSubject.Categorized -> nominalDimensionId == subject.category.dimensionId
    SpendingSubject.Uncategorized -> hasNominalLeg && nominalDimensionId == null
}
