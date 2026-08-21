package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Removes an account that never moved.
 *
 * Refuses an account with movement rather than quietly closing it instead: a use
 * case that silently does something other than its name leaves the caller — and
 * the user reading the button — with a wrong expectation. [ArchiveAccountUseCase]
 * is the action for that one, and the screens offer it by name.
 */
interface DeleteAccountUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The account is resolved **when the operation runs**, so the guards read the
     * account as it is at that instant rather than as a caller once loaded it; an
     * identity that matches nothing is refused with `AccountError.NOT_FOUND` and
     * nothing is removed.
     */
    suspend operator fun invoke(accountId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the account. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(account: Account): Either<Throwable, Unit> = invoke(account.id)
}
