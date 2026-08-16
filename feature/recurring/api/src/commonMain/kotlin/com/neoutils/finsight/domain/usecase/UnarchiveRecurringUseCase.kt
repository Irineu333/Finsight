package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Recurring

/**
 * Puts the recurring back into circulation — reversible, so it is refused by no
 * invariant.
 *
 * Harmless here means **undoing nothing**, not restoring the interval: the cycles
 * that elapsed while it was archived were never generated and are not generated
 * retroactively. Generation resumes from the current cycle.
 *
 * The UI still confirms, unlike the other facades: putting a recurring back is
 * putting a generator back, and the confirmation is where that — and the interval
 * it does not restore — gets said (design D9).
 */
interface UnarchiveRecurringUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The template is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `RecurringError.NOT_FOUND` and nothing is put back.
     */
    suspend operator fun invoke(recurringId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        invoke(recurring.id)
}
