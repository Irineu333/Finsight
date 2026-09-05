package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.repository.IRecurringRepository

class UnarchiveRecurringUseCaseImpl(
    private val repository: IRecurringRepository,
) : UnarchiveRecurringUseCase {

    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> = either {
        // Resolved here and not received, for the same reason archiving resolves: the
        // flag is cleared on the row as it stands, never on a copy loaded earlier.
        val recurring = ensureNotNull(catch { repository.getRecurringById(recurringId) }.bind()) {
            RecurringException(RecurringError.NOT_FOUND)
        }

        catch { repository.update(recurring.copy(isArchived = false)) }.bind()
    }
}
