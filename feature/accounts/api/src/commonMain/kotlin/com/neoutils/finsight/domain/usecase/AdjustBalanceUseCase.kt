package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account
import kotlinx.datetime.LocalDate

/**
 * Corrects an account's balance on a date, by writing the difference.
 *
 * The adjustment is an **event of its date**, not a target the ledger keeps
 * re-reaching: the use case finds the adjustment already written on that date — the
 * transaction on that account carrying an `EQUITY` (reconciliation) counter-leg —
 * and rewrites it from its own ledger leg, so a re-adjustment can never accumulate
 * onto a stale value. An adjustment that lands back on the original balance is
 * removed rather than kept as a transaction worth zero.
 *
 * Adjusting to the balance the account already has is refused with
 * `AccountNotAdjustedException`: there is nothing to record.
 *
 * An adjustment dated after today is refused with `AccountError.ADJUSTMENT_DATE_IN_FUTURE`:
 * it would correct a balance nobody can observe yet.
 */
interface AdjustBalanceUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The account is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `AccountError.NOT_FOUND` and nothing is written.
     */
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        accountId: Long,
    ): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the account. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        account: Account,
    ): Either<Throwable, Unit> = invoke(
        targetBalance = targetBalance,
        adjustmentDate = adjustmentDate,
        accountId = account.id,
    )
}
