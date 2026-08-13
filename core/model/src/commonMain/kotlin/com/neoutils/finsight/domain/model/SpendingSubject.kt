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
