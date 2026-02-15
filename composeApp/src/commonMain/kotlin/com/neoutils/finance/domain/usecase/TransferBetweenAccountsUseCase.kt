@file:OptIn(ExperimentalTime::class)

package com.neoutils.finance.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finance.domain.error.TransferError
import com.neoutils.finance.domain.error.TransferException
import com.neoutils.finance.domain.model.Operation
import com.neoutils.finance.domain.model.Transaction
import com.neoutils.finance.domain.repository.IAccountRepository
import com.neoutils.finance.domain.repository.IOperationRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

class TransferBetweenAccountsUseCase(
    private val operationRepository: IOperationRepository,
    private val accountRepository: IAccountRepository,
) {
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        title: String?,
    ): Either<TransferException, Operation> = either {
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
            operationRepository.createOperation(
                kind = Operation.Kind.TRANSFER,
                title = title?.ifBlank { null } ?: "Transferência entre contas",
                date = date,
                categoryId = null,
                sourceAccountId = sourceAccount.id,
                targetCreditCardId = null,
                targetInvoiceId = null,
                transactions = listOf(
                    Transaction(
                        type = Transaction.Type.EXPENSE,
                        amount = amount,
                        title = title?.ifBlank { null } ?: "Transferência entre contas",
                        date = date,
                        target = Transaction.Target.ACCOUNT,
                        account = sourceAccount,
                    ),
                    Transaction(
                        type = Transaction.Type.INCOME,
                        amount = amount,
                        title = title?.ifBlank { null } ?: "Transferência entre contas",
                        date = date,
                        target = Transaction.Target.ACCOUNT,
                        account = destinationAccount,
                    ),
                ),
            )
        }.mapLeft {
            TransferException(TransferError.Unknown)
        }.bind()
    }
}
