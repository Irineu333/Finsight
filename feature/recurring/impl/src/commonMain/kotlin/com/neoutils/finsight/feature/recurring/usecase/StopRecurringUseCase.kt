package com.neoutils.finsight.feature.recurring.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.feature.recurring.model.Recurring
import com.neoutils.finsight.feature.recurring.repository.IRecurringRepository
class StopRecurringUseCase(
    private val repository: IRecurringRepository,
) {
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        catch { repository.update(recurring.copy(isActive = false)) }
}
