@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.repository.IRecurringOccurrenceRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SkipRecurringUseCase(
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
) {
    suspend operator fun invoke(
        recurring: Recurring,
        date: LocalDate,
    ): Either<Throwable, Unit> = catch {
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
                handledAt = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }
}
