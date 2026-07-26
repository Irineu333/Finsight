package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Removes a recurring that was never used.
 *
 * A recurring in use is refused — see [ResolveRecurringRetirabilityUseCase], the
 * single owner of that rule — and archived instead ([ArchiveRecurringUseCase]).
 */
class DeleteRecurringUseCase(
    private val repository: IRecurringRepository,
    private val resolveRetirability: ResolveRecurringRetirabilityUseCase,
) {
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        when (val retirability = resolveRetirability(recurring)) {
            is RecurringRetirability.MustArchive ->
                RecurringRetireException(retirability.reason).left()

            RecurringRetirability.Deletable -> catch { repository.delete(recurring) }
        }
}
