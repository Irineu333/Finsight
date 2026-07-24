package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.repository.IEntryRepository

class ArchiveAccountUseCaseImpl(
    private val accountDao: AccountDao,
    private val entryRepository: IEntryRepository,
) : ArchiveAccountUseCase {

    override suspend fun invoke(account: Account): Either<Throwable, Unit> {
        // The default account must never be retired: the app must always have one, and
        // archiving it would leave none. The user resolves the *role* first, electing
        // another default (`SetDefaultAccountUseCase`), just as they resolve the balance
        // before archiving. Mirrors the identical guard in `DeleteAccountUseCaseImpl`.
        // Account-only: a card's LIABILITY account is never `isDefault`, so this shared
        // use case never refuses a card here.
        if (account.isDefault) {
            return AccountException(AccountError.CANNOT_ARCHIVE_DEFAULT).left()
        }
        // Only a *permanent* account can hold money that archiving would strand, and
        // archiving does not invent a write-off to zero it: that would put a movement
        // the user never made into their history, replacing the one fact only they
        // have — where the money went — with a generic reconciliation. They resolve
        // it first, by transferring, spending or adjusting.
        //
        // A category is a *temporary* account (INCOME/EXPENSE): its balance is a
        // period total, not money sitting anywhere, and real accounting zeroes it
        // only at period close — which this app never performs. Requiring zero there
        // would make archiving a used category impossible.
        if (account.type.isPermanent && entryRepository.balance(account.id) != 0.0) {
            return AccountException(AccountError.HAS_BALANCE).left()
        }

        return catch { accountDao.close(account.id) }
    }
}
