@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SkipRecurringUseCaseImpl(
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
    // When the cycle was handled is a decision, taken once for the whole app, and not a
    // reading each use case takes off the system: two calls that are the same operation
    // must record the same instant, whatever the millisecond they happen to straddle.
    private val clock: Clock,
) : SkipRecurringUseCase {

    override suspend fun invoke(
        recurringId: Long,
        date: LocalDate,
    ): Either<Throwable, Unit> = catch {
        // Resolved here and not received: the ordinal below is asked of the template, so
        // the occurrence is numbered off the recurring as it is at this instant rather
        // than off a copy a screen loaded earlier.
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: throw RecurringException(RecurringError.NOT_FOUND)

        val yearMonth = date.yearMonth
        // Asked of the template, never counted here: skipping and confirming record the
        // same ordinal about the same month, and two hand-rolled copies of the formula
        // are two chances to disagree. Absent means the series had not begun, which is
        // refused rather than numbered zero.
        val cycleNumber = requireNotNull(recurring.cycleNumberIn(yearMonth)) {
            "Recurring ${recurring.id} has no cycle in $yearMonth: " +
                "its series begins in ${recurring.originMonth}"
        }
        val existingOccurrence = recurringOccurrenceRepository.getOccurrenceBy(recurring.id, yearMonth)

        require(existingOccurrence?.status != RecurringOccurrence.Status.CONFIRMED) {
            "Recurring already confirmed for $yearMonth"
        }

        recurringOccurrenceRepository.save(
            RecurringOccurrence(
                recurringId = recurring.id,
                cycleNumber = cycleNumber,
                yearMonth = yearMonth,
                status = RecurringOccurrence.Status.SKIPPED,
                effectiveDate = date,
                handledAt = clock.now().toEpochMilliseconds(),
            )
        )
    }
}
