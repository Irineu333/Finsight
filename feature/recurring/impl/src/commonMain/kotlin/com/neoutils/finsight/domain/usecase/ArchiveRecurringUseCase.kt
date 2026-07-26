package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Takes the recurring out of circulation. For a recurring, being in circulation
 * *is* generating: from here on it is not presented as pending and produces no new
 * occurrence. The entries it already generated stay intact and still linked to it.
 */
class ArchiveRecurringUseCase(
    private val repository: IRecurringRepository,
) {
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        catch { repository.update(recurring.copy(isArchived = true)) }
}
