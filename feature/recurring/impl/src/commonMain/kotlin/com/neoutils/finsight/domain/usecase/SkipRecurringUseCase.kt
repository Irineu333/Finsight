@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.extension.monthsUntil
import com.neoutils.finsight.extension.toYearMonth
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SkipRecurringUseCase(
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
) {
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
    ): Either<Throwable, Unit> = catch {
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
