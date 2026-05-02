package com.neoutils.finsight.feature.accounts.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.feature.accounts.exception.AccountNotAdjustedException
import com.neoutils.finsight.core.domain.model.Account
import com.neoutils.finsight.feature.transactions.model.Operation
import com.neoutils.finsight.feature.transactions.model.Transaction
import com.neoutils.finsight.feature.transactions.repository.IOperationRepository
import com.neoutils.finsight.feature.transactions.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth
import com.neoutils.finsight.feature.transactions.usecase.ICalculateBalanceUseCase
class AdjustBalanceUseCase(
    private val repository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val calculateBalanceUseCase: ICalculateBalanceUseCase,
) {
    suspend operator fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        account: Account
    ): Either<Throwable, Unit> = either {
        val currentBalance = catch {
            calculateBalanceUseCase(
                target = adjustmentDate.yearMonth,
                accountId = account.id,
            )
        }.bind()

        ensure(targetBalance != currentBalance) { AccountNotAdjustedException() }

        catch {

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
        }.bind()
    }
}