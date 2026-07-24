package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository

/**
 * Brings an archived account back into circulation — the inverse of
 * [ArchiveAccountUseCase]. An account *is* its own chart-of-accounts row, so this
 * reopens that row directly (`reopen`), reverting `accounts.isArchived` and nothing
 * else; the entries were untouched by archiving and stay intact.
 *
 * Reversible and innocuous: no guard, no confirmation. Archiving already required a
 * zero balance, so reopening restores a consistent account. It always comes back a
 * *common* account, never the default — the default can never be archived, so no
 * archived account was ever the default (mirrors `UnarchiveCreditCardUseCase`).
 */
class UnarchiveAccountUseCase(
    private val repository: IAccountRepository,
) {
    suspend operator fun invoke(account: Account): Either<Throwable, Unit> = catch {
        repository.reopen(account.id)
    }
}
