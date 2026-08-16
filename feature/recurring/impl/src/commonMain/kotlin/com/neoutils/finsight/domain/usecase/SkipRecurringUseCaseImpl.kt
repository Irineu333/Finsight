@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SkipRecurringUseCaseImpl(
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
) : SkipRecurringUseCase {

    override suspend fun invoke(
        recurringId: Long,
        date: LocalDate,
    ): Either<Throwable, Unit> = catch {
        // Resolved here and not received: the cycle number is counted from the template's
        // `createdAt`, so the occurrence is numbered off the recurring as it is at this
        // instant rather than off a copy a screen loaded earlier.
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: throw RecurringException(RecurringError.NOT_FOUND)

        val yearMonth = date.yearMonth
        val cycleNumber = Instant
            .fromEpochMilliseconds(recurring.createdAt)
            .toYearMonth()
            .monthsUntil(yearMonth) + 1
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
                handledAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }
}
