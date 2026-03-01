@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringRepository
import com.neoutils.finsight.extension.toYearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class SkipRecurringUseCase(
    private val recurringRepository: IRecurringRepository,
) {
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> = catch {
        recurringRepository.update(
            recurring.copy(lastHandledYearMonth = Clock.System.now().toYearMonth())
        )
    }
}
