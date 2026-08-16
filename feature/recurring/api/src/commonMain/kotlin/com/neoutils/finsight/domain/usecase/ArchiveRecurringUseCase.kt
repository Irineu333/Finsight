package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Recurring

/**
 * Takes the recurring out of circulation. For a recurring, being in circulation
 * *is* generating: from here on it is not presented as pending and produces no new
 * occurrence. The entries it already generated stay intact and still linked to it.
 */
interface ArchiveRecurringUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The template is resolved **when the operation runs**, so the flag is written onto
     * the recurring as it is at that instant rather than onto a copy a screen loaded
     * earlier; an identity that matches nothing is refused with `RecurringError.NOT_FOUND`
     * and nothing is written.
     */
    suspend operator fun invoke(recurringId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        invoke(recurring.id)
}
