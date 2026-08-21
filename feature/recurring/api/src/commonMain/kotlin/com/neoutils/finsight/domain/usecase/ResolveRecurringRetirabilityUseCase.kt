package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringRetirability

/**
 * Resolves, in one place, whether a recurring may be deleted or must be archived.
 * The two guards — a transaction naming it, a budget pointing at it — each name their
 * own `RecurringRetireError`, so [DeleteRecurringUseCase] and the view consume one
 * decision instead of re-deriving it. One owner decides; consumers only read.
 *
 * Occurrences are deliberately not a guard. A skipped cycle writes no transaction,
 * produces no entry and moves no money — refusing removal because of one would refuse
 * the merely inappropriate rather than what breaks an invariant (design D2).
 */
interface ResolveRecurringRetirabilityUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * Both guards are questions about an **identity** — whether anything still points at
     * this template — so the template itself is never loaded, and there is no resolution
     * to fail. An identity nothing points at is [RecurringRetirability.Deletable], which
     * is the honest answer to what this reads; refusing an identity that matches no
     * recurring belongs to the operation that removes one ([DeleteRecurringUseCase]), and
     * that is where it happens.
     */
    suspend operator fun invoke(recurringId: Long): RecurringRetirability

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(recurring: Recurring): RecurringRetirability =
        invoke(recurring.id)
}
