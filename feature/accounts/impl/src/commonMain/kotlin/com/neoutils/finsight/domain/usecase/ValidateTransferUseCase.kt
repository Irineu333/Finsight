@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.raise.either
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
 * The half of the rule that needs nothing but the values stated.
 *
 * Four of the five rules are arithmetic over what the caller said; only "these accounts
 * exist" has to read anything. Separating them is what lets the form enable its button
 * by the very rule the write is refused by, without suspending or reaching a repository
 * — so the button and the boundary cannot come to disagree about what is admissible,
 * there being one statement of it.
 *
 * @param today what the caller's clock says, handed in for the same reason
 * [ValidateTransferUseCase] takes one: the layer that owns a clock is the one that reads it.
 * @param destinationAmount what arrives, when it is not what left. `null` means the two
 * ends are the same number, and there is no second figure to judge.
 * @return the first rule broken, in the order the refusals are reported, or `null` when
 * none is.
 */
internal fun transferRuleBroken(
    sourceAccountId: Long,
    destinationAccountId: Long,
    amount: Double,
    date: LocalDate,
    today: LocalDate,
    destinationAmount: Double? = null,
): TransferError? = when {
    amount <= 0.0 -> TransferError.InvalidAmount
    destinationAmount != null && destinationAmount <= 0.0 -> TransferError.InvalidAmount
    sourceAccountId == destinationAccountId -> TransferError.SameAccount
    date > today -> TransferError.FutureDate
    else -> null
}

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
        transferRuleBroken(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            today = clock.today(),
            destinationAmount = destinationAmount,
        )?.let { raise(it) }

        val source = accountRepository.getAccountById(sourceAccountId)
        ensureNotNull(source) { TransferError.SourceAccountNotFound }

        val destination = accountRepository.getAccountById(destinationAccountId)
        ensureNotNull(destination) { TransferError.DestinationAccountNotFound }

        ValidatedTransfer(source = source, destination = destination)
    }
}
