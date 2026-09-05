package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.model.Transaction
import kotlinx.datetime.LocalDate

/**
 * Money moving between two of the user's own accounts.
 *
 * **When the two differ in currency the caller states both ends**, because that is what
 * the statement shows: R$ 550 left here, US$ 100 arrived there. No rate is a parameter
 * anywhere on this path — the rate is a quotient of the two ends and is *derived* from
 * them afterwards (design D6). The write boundary is what completes the intent, posting
 * the residue of each currency to that currency's conversion account, so nothing here
 * has to know how a cross-currency transaction balances.
 */
interface TransferBetweenAccountsUseCase {

    /**
     * Both accounts are resolved **when the operation runs**; an identity that matches
     * nothing is refused with `TransferError.SourceAccountNotFound` or
     * `TransferError.DestinationAccountNotFound`, and nothing is written.
     *
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number, which is the whole of the mono-currency case.
     * @param title why the money moved, as the user stated it, and `null` when they had
     * nothing to state. It names the operation and classifies nothing: a transfer has no
     * analytic axis, and a title does not give it one.
     */
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double? = null,
        title: String? = null,
    ): Either<TransferException, Transaction>
}
