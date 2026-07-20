@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.database.dao.AccountDao
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.IEntryRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class CloseAccountUseCaseImpl(
    private val accountDao: AccountDao,
    private val accountRepository: IAccountRepository,
    private val entryRepository: IEntryRepository,
    private val transactionRepository: ITransactionRepository,
) : CloseAccountUseCase {


    override suspend fun invoke(account: Account): Either<Throwable, CloseAccountUseCase.Outcome> = catch {
        if (accountDao.entryCount(account.id) == 0) {
            accountRepository.delete(account)
            return@catch CloseAccountUseCase.Outcome.DELETED
        }

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

        if (balance != 0.0) {
            CloseAccountUseCase.Outcome.CLOSED_WITH_WRITE_OFF
        } else {
            CloseAccountUseCase.Outcome.CLOSED
        }
    }
}
