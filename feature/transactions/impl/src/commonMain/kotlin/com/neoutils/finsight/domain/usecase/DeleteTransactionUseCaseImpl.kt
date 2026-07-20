package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.ITransactionRepository

class DeleteTransactionUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
) : DeleteTransactionUseCase {
    override suspend fun invoke(transaction: Transaction): Either<Throwable, Unit> = catch {
        transactionRepository.deleteTransactionById(transaction.id)
    }
}
