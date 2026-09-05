package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.repository.IAccountRepository

class SetDefaultAccountUseCaseImpl(
    private val repository: IAccountRepository
) : SetDefaultAccountUseCase {

    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        val accounts = catch { repository.getAllAccounts() }.bind()

        // The election is exclusive: the loop below demotes whoever holds the role. An
        // identity matching no open account would demote the incumbent and promote
        // nobody, leaving the app with no default at all — so it is refused first.
        ensure(accounts.any { it.id == accountId }) {
            AccountException(AccountError.NOT_FOUND)
        }

        catch {
            accounts.forEach { account ->
                if (account.id == accountId && !account.isDefault) {
                    repository.update(account.copy(isDefault = true))
                } else if (account.id != accountId && account.isDefault) {
                    repository.update(account.copy(isDefault = false))
                }
            }
        }.bind()
    }
}
