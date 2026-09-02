@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.FakeAccountRepository
import com.neoutils.finsight.FakeRecurringOccurrenceRepository
import com.neoutils.finsight.FakeRecurringRepository
import com.neoutils.finsight.StoppedClock
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.CreditCard
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.recurring
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.YearMonth
import kotlinx.datetime.atStartOfDayIn
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.ExperimentalTime

/**
 * **A cycle before the series began is refused, not numbered.**
 *
 * Both paths that write an occurrence used to number it by hand, with a subtraction that
 * has no floor: a date in a month before the template's anchor produced cycle 0, then −1,
 * and persisted it — into the occurrence and into the transaction's `recurringCycle`,
 * from where a detail line read "Aluguel • 0".
 *
 * The domain refuses it, and does so before writing anything. The date picker of the
 * confirmation is what makes the refusal unreachable by the designed path; this is the
 * net behind it, in the same shape as the currency refusal beside it.
 */
class RecurringCycleFloorTest {

    private val august = YearMonth(2026, 8)

    private val account = Account(
        id = 1,
        name = "Nubank",
        type = AccountType.ASSET,
        currency = "BRL",
    )

    // A template that would confirm perfectly well if the month were right: it names an
    // account, in the account's own currency. Without it the confirmation refuses for a
    // reason of its own, and the test would pass green over an unfixed floor.
    private val template = recurring(id = 1L).copy(
        account = account,
        createdAt = LocalDate(2026, 8, 1)
            .atStartOfDayIn(TimeZone.currentSystemDefault())
            .toEpochMilliseconds(),
    )

    private object UnusedInvoices : GetOrCreateInvoiceForMonthUseCase {
        override suspend fun invoke(
            creditCardId: Long,
            targetDueMonth: YearMonth,
        ): arrow.core.Either<Throwable, Invoice> = throw NotImplementedError()
    }

    private fun confirmUseCase(occurrences: FakeRecurringOccurrenceRepository) =
        ConfirmRecurringUseCaseImpl(
            recurringRepository = FakeRecurringRepository(listOf(template)),
            recurringOccurrenceRepository = occurrences,
            getOrCreateInvoiceForMonthUseCase = UnusedInvoices,
            accountRepository = FakeAccountRepository(listOf(account)),
            clock = StoppedClock(),
        )

    @Test
    fun `confirming into a month before the series began is refused`() = runTest {
        val occurrences = FakeRecurringOccurrenceRepository()

        val result = confirmUseCase(occurrences)(
            recurring = template,
            date = LocalDate(2026, 7, 5),
        )

        // Refused before anything is written: the occurrence repository is a fake whose
        // writes throw, and the failure that comes back is the refusal, not a write.
        assertIs<IllegalArgumentException>(result.leftOrNull())
        assertTrue(occurrences.all.value.isEmpty())
    }

    @Test
    fun `skipping a month before the series began is refused`() = runTest {
        val occurrences = FakeRecurringOccurrenceRepository()

        val result = SkipRecurringUseCaseImpl(
            recurringRepository = FakeRecurringRepository(listOf(template)),
            recurringOccurrenceRepository = occurrences,
            clock = StoppedClock(),
        )(
            recurring = template,
            date = LocalDate(2026, 7, 5),
        )

        assertIs<IllegalArgumentException>(result.leftOrNull())
    }

    /** The origin month itself is cycle 1, and nothing refuses it. */
    @Test
    fun `the origin month is not refused by the floor`() = runTest {
        val occurrences = FakeRecurringOccurrenceRepository()

        val result = SkipRecurringUseCaseImpl(
            recurringRepository = FakeRecurringRepository(listOf(template)),
            recurringOccurrenceRepository = occurrences,
            clock = StoppedClock(),
        )(
            recurring = template,
            date = LocalDate(august.year, august.month, 5),
        )

        // It gets past the floor and dies on the fake's write, which is what says the
        // refusal is not swallowing the legitimate month.
        assertIs<NotImplementedError>(result.leftOrNull())
    }
}
