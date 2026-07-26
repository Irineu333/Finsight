package com.neoutils.finsight.domain.model

import com.neoutils.finsight.domain.error.RecurringRetireError

/**
 * Whether a recurring may be deleted or must be archived — the single answer to "what
 * happens when the user retires this recurring". Resolved in one place from the two
 * guards (a transaction naming it, a budget pointing at it) so no screen re-derives
 * which action applies.
 *
 * It mirrors [CategoryRetirability] without sharing it. The shape coincides; the
 * content does not. The reasons are what a `Retirability` carries — strip them and
 * `Deletable | MustArchive` is a boolean — and a category's reasons cannot happen to
 * a recurring, nor a recurring's to a category. Sharing would promote a type used by
 * one facade to one used by two, not unify four (design D4).
 */
sealed interface RecurringRetirability {

    /** No dependents: the recurring can be removed outright. */
    data object Deletable : RecurringRetirability

    /** A dependent exists ([reason]): the recurring is kept and only archived. */
    data class MustArchive(val reason: RecurringRetireError) : RecurringRetirability
}
