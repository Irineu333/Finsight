package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.IRecurringRepository

class DeleteAccountUseCaseImpl(
    private val accountRepository: IAccountRepository,
    private val entryRepository: IEntryRepository,
    private val recurringRepository: IRecurringRepository,
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
        // Same shape of guard: the recurring FK is SET_NULL, so deleting would
        // strip the link rather than fail, and the template would survive with
        // nothing to post through.
        if (recurringRepository.hasRecurringForAccount(account.id)) {
            return AccountException(AccountError.HAS_RECURRING).left()
        }
        return catch { accountRepository.delete(account) }
    }
}
