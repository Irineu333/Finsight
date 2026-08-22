@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.extension.today
import kotlinx.datetime.LocalDate
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * The two ends a valid transfer has, resolved from the ids the caller stated.
 *
 * They are handed back rather than discarded because "these accounts exist" is one of
 * the rules checked here: a caller that had to read them again would repeat the very
 * lookup whose failure this use case already named.
 */
data class ValidatedTransfer(
    val source: Account,
    val destination: Account,
)

/**
 * What makes a transfer between accounts admissible, with a single owner.
 *
 * The same five rules govern registering a transfer and correcting one, so they are
 * stated once and consumed twice — the relation [CreateAccountUseCase] and
 * [UpdateAccountUseCase] already have with [ValidateAccountNameUseCase]. Two copies
 * would diverge with nothing to report it.
 *
 * The [clock] is injected rather than read from the system, so this rule and the form
 * that feeds it cannot disagree about what "today" is — the form bounds its date picker
 * by the very same clock.
 */
class ValidateTransferUseCase(
    private val accountRepository: IAccountRepository,
    private val clock: Clock,
) {
    /**
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number, and there is no second figure to judge.
     */
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double? = null,
    ): Either<TransferError, ValidatedTransfer> = either {
        ensure(amount > 0.0) { TransferError.InvalidAmount }

        ensure(destinationAmount == null || destinationAmount > 0.0) { TransferError.InvalidAmount }

        ensure(sourceAccountId != destinationAccountId) { TransferError.SameAccount }

        ensure(date <= clock.today()) { TransferError.FutureDate }

        val source = accountRepository.getAccountById(sourceAccountId)
        ensureNotNull(source) { TransferError.SourceAccountNotFound }

        val destination = accountRepository.getAccountById(destinationAccountId)
        ensureNotNull(destination) { TransferError.DestinationAccountNotFound }

        ValidatedTransfer(source = source, destination = destination)
    }
}
