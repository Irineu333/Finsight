package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Recurring
import kotlinx.datetime.LocalDate

/**
 * Records that one cycle of a recurring was deliberately passed over.
 *
 * A skip writes no transaction and produces no entry: it exists so the month stops
 * being offered for confirmation, and so the pass is a fact rather than the absence
 * of one. [date] decides which month the occurrence is filed under, which is the whole
 * content of the decision — it is never re-derived from the clock.
 *
 * A cycle already confirmed cannot be skipped: the money moved, and the occurrence
 * that says so is not overwritten.
 */
interface SkipRecurringUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The template is resolved **when the operation runs** — the cycle number is counted
     * from its `createdAt`, so a copy loaded earlier could number the occurrence off a
     * value that has since changed. An identity that matches nothing is refused with
     * `RecurringError.NOT_FOUND` and no occurrence is written.
     */
    suspend operator fun invoke(recurringId: Long, date: LocalDate): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the recurring. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(recurring: Recurring, date: LocalDate): Either<Throwable, Unit> =
        invoke(recurring.id, date)
}
