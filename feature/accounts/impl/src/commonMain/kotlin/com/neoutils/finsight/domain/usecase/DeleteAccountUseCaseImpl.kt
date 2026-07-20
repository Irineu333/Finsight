package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IEntryRepository

class DeleteAccountUseCaseImpl(
    private val accountRepository: IAccountRepository,
    private val entryRepository: IEntryRepository,
) : DeleteAccountUseCase {

    override suspend fun invoke(account: Account): Either<Throwable, Unit> {
        if (account.isDefault) {
            return AccountException(AccountError.CANNOT_DELETE_DEFAULT).left()
        }
        // The guard, not merely a hint to the UI: `entries.accountId` is NO ACTION,
        // so removing the row would either fail at the FK or strand the history.
        if (entryRepository.hasEntries(account.id)) {
            return AccountException(AccountError.HAS_TRANSACTIONS).left()
        }
        return catch { accountRepository.delete(account) }
    }
}
