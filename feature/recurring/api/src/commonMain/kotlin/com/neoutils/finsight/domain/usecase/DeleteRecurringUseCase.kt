package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Recurring

/**
 * Removes a recurring that was never used.
 *
 * A recurring in use is refused — see [ResolveRecurringRetirabilityUseCase], the
 * single owner of that rule — and archived instead ([ArchiveRecurringUseCase]).
 */
interface DeleteRecurringUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The template is resolved **when the operation runs**, so the guards decide on the
     * recurring as it is at that instant: one that has taken on a transaction since a
     * screen loaded it is now refused. An identity that matches nothing is refused with
     * `RecurringError.NOT_FOUND` and nothing is removed.
     */
    suspend operator fun invoke(recurringId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        invoke(recurring.id)
}
