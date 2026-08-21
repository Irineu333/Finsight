package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.RecurringError
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.repository.IRecurringRepository

class ArchiveRecurringUseCaseImpl(
    private val repository: IRecurringRepository,
) : ArchiveRecurringUseCase {

    override suspend fun invoke(recurringId: Long): Either<Throwable, Unit> = either {
        // Resolved here and not received: the flag is written onto the template as it is
        // at this instant, so a screen that loaded it minutes ago cannot carry a stale
        // copy of everything else back into the row along with the flag.
        val recurring = ensureNotNull(catch { repository.getRecurringById(recurringId) }.bind()) {
            RecurringException(RecurringError.NOT_FOUND)
        }

        catch { repository.update(recurring.copy(isArchived = true)) }.bind()
    }
}
