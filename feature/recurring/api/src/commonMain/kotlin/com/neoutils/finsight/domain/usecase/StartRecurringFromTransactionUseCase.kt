@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.flatMap
import com.neoutils.finsight.domain.exception.RecurringException
import com.neoutils.finsight.domain.model.RecurringOccurrence
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.form.RecurringForm
import com.neoutils.finsight.domain.repository.IRecurringRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.yearMonth
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Creates the recurring a transaction is the **first cycle** of.
 *
 * The template is anchored on the transaction's own date rather than on the clock, and
 * that is what makes the cycle a 1: `ConfirmRecurringUseCase` counts a cycle as
 * `createdAt→month .monthsUntil(month) + 1`, and with both months being the same the
 * formula yields `0 + 1`. The number is not fixed here on the side — it is that same
 * rule in its degenerate case, which is why a transaction dated last month produces a
 * template whose cycle 1 *is* last month.
 *
 * The occurrence is not a nicety. Pending cycles are decided by the day having arrived
 * and no occurrence existing for the month, and a template born with the day of its own
 * transaction satisfies both at once: without the confirmed occurrence the month would
 * be offered for confirmation immediately, and confirming it would write the same
 * expense into the ledger a second time.
 *
 * [firstCycle] arrives already built — invoice resolved, legs and contra decided — and
 * is completed, never rebuilt: it carries the invoice the user picked on the screen,
 * which re-deriving it from the template would silently replace.
 */
class StartRecurringFromTransactionUseCase(
    private val repository: IRecurringRepository,
    private val clock: Clock,
) {
    suspend operator fun invoke(
        form: RecurringForm,
        firstCycle: TransactionIntent,
    ): Either<Throwable, Transaction> {
        val date = firstCycle.date

        // The zone is not free: `Instant.toYearMonth` reads it back in the system
        // default, so anchoring anywhere else would slide the month at the edges.
        val createdAt = date
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds()

        return form
            .toRecurring(createdAt)
            .mapLeft { RecurringException(it) }
            .flatMap { recurring ->
                catch {
                    repository.createWithFirstCycle(
                        recurring = recurring,
                        firstCycle = firstCycle.copy(recurringCycle = CYCLE_ONE),
                        occurrence = RecurringOccurrence(
                            recurringId = recurring.id,
                            cycleNumber = CYCLE_ONE,
                            yearMonth = date.yearMonth,
                            status = RecurringOccurrence.Status.CONFIRMED,
                            effectiveDate = date,
                            handledAt = clock.now().toEpochMilliseconds(),
                        ),
                    )
                }
            }
    }

    private companion object {
        /** The cycle of a template anchored on the very transaction that opens it. */
        const val CYCLE_ONE = 1
    }
}
