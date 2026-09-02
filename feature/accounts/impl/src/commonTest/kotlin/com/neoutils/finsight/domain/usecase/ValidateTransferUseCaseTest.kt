@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.model.Account
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.ExperimentalTime

/**
 * The five rules a transfer has to satisfy, now with one owner instead of being
 * embedded in the use case that registers one.
 *
 * The clock is handed in, which is the whole reason the future-date rule is testable
 * here at all: while it read `Clock.System`, "not in the future" could only be
 * exercised against the machine's real calendar.
 */
class ValidateTransferUseCaseTest {

    private val today = LocalDate(2026, 3, 10)

    private val source = Account(id = 1, name = "Nubank", currency = "BRL")
    private val destination = Account(id = 2, name = "Chase", currency = "USD")

    private fun useCase(accounts: List<Account> = listOf(source, destination)) =
        ValidateTransferUseCase(
            accountRepository = StaticAccounts(accounts),
            clock = ClockOn(today),
        )

    @Test
    fun `a transfer of two existing accounts on a past date is admitted`() = runTest {
        val validated = useCase()(
            sourceAccountId = source.id,
            destinationAccountId = destination.id,
            amount = 550.0,
            date = today,
            destinationAmount = 100.0,
        ).getOrNull()

        assertEquals(source, validated?.source)
        assertEquals(destination, validated?.destination)
    }

    @Test
    fun `an amount of zero or less is refused`() = runTest {
        assertEquals(
            TransferError.InvalidAmount,
            useCase()(
                sourceAccountId = source.id,
                destinationAccountId = destination.id,
                amount = 0.0,
                date = today,
            ).leftOrNull(),
        )
    }

    @Test
    fun `a destination amount of zero or less is refused`() = runTest {
        assertEquals(
            TransferError.InvalidAmount,
            useCase()(
                sourceAccountId = source.id,
                destinationAccountId = destination.id,
                amount = 550.0,
                date = today,
                destinationAmount = 0.0,
            ).leftOrNull(),
        )
    }

    @Test
    fun `the same account on both ends is refused`() = runTest {
        assertEquals(
            TransferError.SameAccount,
            useCase()(
                sourceAccountId = source.id,
                destinationAccountId = source.id,
                amount = 550.0,
                date = today,
            ).leftOrNull(),
        )
    }

    @Test
    fun `a date in the future is refused, against the clock handed in`() = runTest {
        assertEquals(
            TransferError.FutureDate,
            useCase()(
                sourceAccountId = source.id,
                destinationAccountId = destination.id,
                amount = 550.0,
                date = LocalDate(2026, 3, 11),
            ).leftOrNull(),
        )
    }

    @Test
    fun `a source account that does not exist is refused`() = runTest {
        assertEquals(
            TransferError.SourceAccountNotFound,
            useCase(accounts = listOf(destination))(
                sourceAccountId = source.id,
                destinationAccountId = destination.id,
                amount = 550.0,
                date = today,
            ).leftOrNull(),
        )
    }

    @Test
    fun `a destination account that does not exist is refused`() = runTest {
        assertEquals(
            TransferError.DestinationAccountNotFound,
            useCase(accounts = listOf(source))(
                sourceAccountId = source.id,
                destinationAccountId = destination.id,
                amount = 550.0,
                date = today,
            ).leftOrNull(),
        )
    }
}
