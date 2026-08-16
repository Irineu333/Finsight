package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Updates an account — everything about it except the one attribute that is its
 * identity.
 *
 * **The currency is refused unconditionally** (design D12): not "once the account has
 * entries", but always, and the refusal reads no state at all. A conditional refusal is
 * one somebody has to remember to keep correct, and the condition would be answering a
 * question that does not apply — currency is an attribute of identity, not of history.
 *
 * The rule lives here, in the domain, and not only in the form that hides the control:
 * the project's own layering forbids the inversion where a screen is the only thing
 * keeping an invariant.
 */
interface UpdateAccountUseCase {

    /**
     * The account is resolved **when the operation runs** and handed to [update], so
     * the edit is applied to the account as it is at that instant; an identity that
     * matches nothing is refused with `AccountError.NOT_FOUND`.
     */
    suspend operator fun invoke(
        accountId: Long,
        update: (Account) -> Account,
    ): Either<Throwable, Account>
}
