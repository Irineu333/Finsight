@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class TransferBetweenAccountsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
) {
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
    ): Either<TransferException, Transaction> = either {
        ensure(amount > 0.0) {
            TransferException(TransferError.InvalidAmount)
        }

        ensure(sourceAccountId != destinationAccountId) {
            TransferException(TransferError.SameAccount)
        }

        ensure(date <= currentDate) {
            TransferException(TransferError.FutureDate)
        }

        val sourceAccount = accountRepository.getAccountById(sourceAccountId)
        ensureNotNull(sourceAccount) {
            TransferException(TransferError.SourceAccountNotFound)
        }

        val destinationAccount = accountRepository.getAccountById(destinationAccountId)
        ensureNotNull(destinationAccount) {
            TransferException(TransferError.DestinationAccountNotFound)
        }

        catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.EXPENSE,
                            amount = amount,
                            accountId = sourceAccount.id,
                        ),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = amount,
                            accountId = destinationAccount.id,
                        ),
                    ),
                )
            )
        }.mapLeft {
            TransferException(TransferError.Unknown)
        }.bind()
    }
}
