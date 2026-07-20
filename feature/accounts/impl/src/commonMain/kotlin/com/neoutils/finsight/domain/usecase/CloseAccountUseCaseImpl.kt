@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.database.dao.AccountDao
import arrow.core.left
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CloseAccountUseCaseImpl(
    private val accountDao: AccountDao,
    private val entryRepository: IEntryRepository,
    private val transactionRepository: ITransactionRepository,
) : CloseAccountUseCase {


    override suspend fun invoke(account: Account): Either<Throwable, Unit> {
        // Closing an account that never moved would hide it with nothing preserved,
        // and there is no way back. Deleting is the action for that one.
        if (!entryRepository.hasEntries(account.id)) {
            return AccountException(AccountError.NO_TRANSACTIONS).left()
        }
        return close(account)
    }

    private suspend fun close(account: Account): Either<Throwable, Unit> = catch {
        val balance = entryRepository.balance(account.id)

        // The write-off comes first: once the account is closed it is out of the
        // active listings, and a failure here would strand the money unrecorded.
        if (balance != 0.0) {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.ADJUSTMENT,
                            amount = -balance,
                            account = account,
                        )
                    ),
                )
            )
        }

        accountDao.close(account.id)
    }
}
