package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.left
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class AdjustBalanceUseCase(
    private val repository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
) {
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        account: Account
    ): Either<Throwable, Unit> {
        val currentBalance = calculateBalanceUseCase(
            target = adjustmentDate.yearMonth,
            accountId = account.id,
        )

        if (targetBalance == currentBalance) return AccountNotAdjustedException().left()

        return catch {

            val existingAdjustment = repository.getTransactionsBy(
                type = Transaction.Type.ADJUSTMENT,
                target = Transaction.Target.ACCOUNT,
                date = adjustmentDate,
                accountId = account.id,
            ).firstOrNull()

            val difference = targetBalance - currentBalance

            if (existingAdjustment == null) {
                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = null,
                    date = adjustmentDate,
                    categoryId = null,
                    sourceAccountId = account.id,
                    targetCreditCardId = null,
                    targetInvoiceId = null,
                    transactions = listOf(
                        Transaction(
                            title = null,
                            type = Transaction.Type.ADJUSTMENT,
                            amount = difference,
                            date = adjustmentDate,
                            account = account,
                        )
                    ),
                )
                return@catch
            }

            val newAmount = existingAdjustment.amount + difference

            if (newAmount == 0.0) {
                val operationId = existingAdjustment.operationId
                if (operationId != null) {
                    operationRepository.deleteOperationById(operationId)
                } else {
                    repository.delete(existingAdjustment)
                }
                return@catch
            }

            repository.update(
                existingAdjustment.copy(amount = newAmount)
            )
        }
    }
}