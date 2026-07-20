package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IEntryRepository

class CloseAccountUseCaseImpl(
    private val accountDao: AccountDao,
    private val entryRepository: IEntryRepository,
) : CloseAccountUseCase {

    override suspend fun invoke(account: Account): Either<Throwable, Unit> {
        // Closing an account that never moved would hide it with nothing preserved,
        // and there is no way back. Deleting is the action for that one.
        if (!entryRepository.hasEntries(account.id)) {
            return AccountException(AccountError.NO_TRANSACTIONS).left()
        }

        // Only a *monetary* account can hold money that closing would strand, and
        // closing does not invent a write-off to zero it: that would put a movement
        // the user never made into their history, replacing the one fact only they
        // have — where the money went — with a generic reconciliation. They resolve
        // it first, by transferring, spending or adjusting.
        //
        // A category is an INCOME/EXPENSE account: its balance is accumulated flow,
        // not money sitting anywhere, and it is never zero once used. Requiring zero
        // there would make closing a used category impossible.
        if (account.type.isMonetary && entryRepository.balance(account.id) != 0.0) {
            return AccountException(AccountError.HAS_BALANCE).left()
        }

        return catch { accountDao.close(account.id) }
    }
}
