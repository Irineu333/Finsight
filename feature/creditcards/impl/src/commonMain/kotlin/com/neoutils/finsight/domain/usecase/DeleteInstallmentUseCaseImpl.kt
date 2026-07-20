package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import com.neoutils.finsight.domain.model.Installment
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IInstallmentRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository

class DeleteInstallmentUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val installmentRepository: IInstallmentRepository,
) : DeleteInstallmentUseCase {
    override suspend fun invoke(
        installment: Installment,
        transactions: List<Transaction>,
    ): Either<Throwable, Unit> = catch {
        transactionRepository.deleteTransactionsByIds(transactions.map { it.id })
        installmentRepository.deleteInstallmentById(installment.id)
    }
}
