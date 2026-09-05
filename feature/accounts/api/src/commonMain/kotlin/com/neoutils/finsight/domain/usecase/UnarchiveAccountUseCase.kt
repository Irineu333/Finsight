package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import com.neoutils.finsight.domain.model.Account

/**
 * Brings an archived account back into circulation — the inverse of
 * [ArchiveAccountUseCase]. An account *is* its own chart-of-accounts row, so this
 * reopens that row directly, reverting `accounts.isArchived` and nothing else; the
 * entries were untouched by archiving and stay intact.
 *
 * Reversible and innocuous: no guard, no confirmation. Archiving already required a
 * zero balance, so reopening restores a consistent account. It always comes back a
 * *common* account, never the default — the default can never be archived, so no
 * archived account was ever the default (mirrors `UnarchiveCreditCardUseCase`).
 */
interface UnarchiveAccountUseCase {

    /**
     * The canonical form, and the one that carries the implementation.
     *
     * The account is resolved **when the operation runs**; an identity that matches
     * nothing is refused with `AccountError.NOT_FOUND` and nothing is reopened.
     */
    suspend operator fun invoke(accountId: Long): Either<Throwable, Unit>

    /**
     * The convenience for a caller that already holds the account. It extracts the
     * identity and delegates — not another rule, so not another implementation.
     */
    suspend operator fun invoke(account: Account): Either<Throwable, Unit> = invoke(account.id)
}
