package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransactionError
import com.neoutils.finsight.domain.exception.TransactionException
import com.neoutils.finsight.domain.repository.ITransactionRepository

class DeleteTransactionUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
) : DeleteTransactionUseCase {

    override suspend fun invoke(transactionId: Long): Either<Throwable, Unit> = either {
        // Resolved before the removal so that an identity matching nothing is a refusal
        // and not a silent success: `deleteTransactionById` removes by id, and asking it
        // to remove a row that is not there does exactly nothing, quietly.
        ensureNotNull(catch { transactionRepository.getTransactionById(transactionId) }.bind()) {
            TransactionException(TransactionError.NOT_FOUND)
        }

        catch { transactionRepository.deleteTransactionById(transactionId) }.bind()
    }
}
