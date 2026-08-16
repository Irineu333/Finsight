package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.repository.IAccountRepository

class UnarchiveAccountUseCaseImpl(
    private val repository: IAccountRepository,
) : UnarchiveAccountUseCase {

    override suspend fun invoke(accountId: Long): Either<Throwable, Unit> = either {
        // Reopening is a blind `UPDATE` by id, which touches nothing when the id matches
        // nothing: without this the caller would be told the account came back.
        ensureNotNull(catch { repository.getAccountById(accountId) }.bind()) {
            AccountException(AccountError.NOT_FOUND)
        }

        catch { repository.reopen(accountId) }.bind()
    }
}
