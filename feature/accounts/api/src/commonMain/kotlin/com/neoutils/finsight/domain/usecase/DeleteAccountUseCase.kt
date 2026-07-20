package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Removes an account that never moved.
 *
 * Refuses an account with movement rather than quietly closing it instead: a use
 * case that silently does something other than its name leaves the caller — and
 * the user reading the button — with a wrong expectation. [CloseAccountUseCase]
 * is the action for that one, and the screens offer it by name.
 */
interface DeleteAccountUseCase {
    suspend operator fun invoke(account: Account): Either<Throwable, Unit>
}
