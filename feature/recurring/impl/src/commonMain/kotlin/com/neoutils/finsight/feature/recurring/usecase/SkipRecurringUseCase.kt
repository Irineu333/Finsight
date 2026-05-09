@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.feature.recurring.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.core.utils.extension.monthsUntil
import com.neoutils.finsight.core.utils.extension.toYearMonth
import com.neoutils.finsight.feature.recurring.error.RecurringError
import com.neoutils.finsight.feature.recurring.exception.RecurringException
import com.neoutils.finsight.feature.recurring.model.RecurringOccurrence
import com.neoutils.finsight.feature.recurring.repository.IRecurringOccurrenceRepository
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class SkipRecurringUseCase(
    private val recurringRepository: IRecurringRepository,
    private val recurringOccurrenceRepository: IRecurringOccurrenceRepository,
) {
    suspend operator fun invoke(
        recurringId: Long,
        date: LocalDate,
    ): Either<Throwable, Unit> {
        val recurring = recurringRepository.getRecurringById(recurringId)
            ?: return Either.Left(RecurringException(RecurringError.NOT_FOUND))

        return catch {
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
}
