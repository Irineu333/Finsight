package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Recurring
import com.neoutils.finsight.domain.repository.IRecurringRepository

/**
 * Puts the recurring back into circulation — reversible and harmless, so it is
 * refused by no invariant and asks for no destructive confirmation.
 *
 * Harmless here means **undoing nothing**, not restoring the interval: the cycles
 * that elapsed while it was archived were never generated and are not generated
 * retroactively. Generation resumes from the current cycle.
 */
class UnarchiveRecurringUseCase(
    private val repository: IRecurringRepository,
) {
    suspend operator fun invoke(recurring: Recurring): Either<Throwable, Unit> =
        catch { repository.update(recurring.copy(isArchived = false)) }
}
