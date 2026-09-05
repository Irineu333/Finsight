package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.exception.RecurringRetireException
import com.neoutils.finsight.domain.model.RecurringRetirability
import com.neoutils.finsight.domain.repository.IRecurringRepository

class DeleteRecurringUseCaseImpl(
    private val repository: IRecurringRepository,
    private val resolveRetirability: ResolveRecurringRetirabilityUseCase,
) : DeleteRecurringUseCase {

    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> {
        // Resolved here and not received: the guards below decide on the recurring as it
        // is at this instant, so a screen that loaded it minutes ago cannot delete a
        // template that has since produced a transaction.
        val recurring = repository.getRecurringById(recurringId)
            ?: return RecurringException(RecurringError.NOT_FOUND).left()

        return when (val retirability = resolveRetirability(recurring.id)) {
            is RecurringRetirability.MustArchive ->
                RecurringRetireException(retirability.reason).left()

            RecurringRetirability.Deletable -> catch { repository.delete(recurring) }
        }
    }
}
