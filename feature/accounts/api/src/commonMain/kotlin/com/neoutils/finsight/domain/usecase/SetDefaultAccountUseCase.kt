package com.neoutils.finsight.domain.usecase

import arrow.core.Either

/**
 * Elects the account the app offers first, and demotes whichever held the role.
 *
 * The role is exclusive and always filled: the app must have exactly one default, and
 * that is why an identity matching no open account is refused instead of clearing the
 * role and leaving none.
 */
interface SetDefaultAccountUseCase {

    /**
     * The account is resolved **when the operation runs**; an identity that matches no
     * open account is refused with `AccountError.NOT_FOUND` and no account changes.
     */
    suspend operator fun invoke(accountId: Long): Either<Throwable, Unit>
}
