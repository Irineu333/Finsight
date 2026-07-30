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

/**
 * A transfer of two amounts, one per end.
 *
 * [destinationAmount] has **no default**: when the two accounts share a currency the caller
 * passes the same number twice, and when they do not, forgetting it is a compile error rather
 * than a transfer that silently credits the wrong figure. The two ends being equal in the
 * same-currency case is not re-checked here — `Σ = 0` per currency has one owner, the write
 * boundary, and it refuses an unbalanced single-currency operation exactly as it does today.
 */
class TransferBetweenAccountsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val collectOperationRate: CollectOperationRateUseCase,
) {
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        sourceAmount: Double,
        destinationAmount: Double,
        date: LocalDate,
    ): Either<TransferException, Transaction> = either {
        ensure(sourceAmount > 0.0 && destinationAmount > 0.0) {
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

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.EXPENSE,
                            amount = sourceAmount,
                            accountId = sourceAccount.id,
                        ),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = destinationAmount,
                            accountId = destinationAccount.id,
                        ),
                    ),
                )
            )
        }.mapLeft {
            TransferException(TransferError.Unknown)
        }.bind()

        // After the operation, and unable to undo it: the transfer is the fact, the rate is
        // what it taught. A rate that fails to record leaves a figure approximate until the
        // next operation or a typed quote — a state design D9 already defines as legitimate —
        // whereas refusing a valid transfer over it would invert the two.
        catch {
            collectOperationRate(
                sourceCurrency = sourceAccount.currency,
                sourceAmount = sourceAmount,
                destinationCurrency = destinationAccount.currency,
                destinationAmount = destinationAmount,
                date = date,
            )
        }

        transaction
    }
}
