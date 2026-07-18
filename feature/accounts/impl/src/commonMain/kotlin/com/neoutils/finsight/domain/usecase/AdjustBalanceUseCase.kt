package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.first
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
    ): Either<Throwable, Unit> = either {
        val currentBalance = catch {
            calculateBalanceUseCase(
                target = adjustmentDate.yearMonth,
                accountId = account.id,
            )
        }.bind()

        ensure(targetBalance != currentBalance) { AccountNotAdjustedException() }

        catch {

            // Idempotency over the ledger (task 4.12): the existing adjustment is the
            // operation on this date with a leg on this account and an EQUITY (reconciliation)
            // counter-leg — the ledger shape of "an account adjustment", not a lookup by
            // `Transaction.Type`/`Target` (both removed in §6). Its legacy leg is taken from
            // the hydrated operation so the double-write path stays intact until §6.
            val existingOperation = operationRepository
                .observeOperationsBy(date = adjustmentDate, accountId = account.id)
                .first()
                .firstOrNull { operation ->
                    operation.entries.any { it.account.type == AccountType.EQUITY } &&
                        operation.entries.any { it.account.id == account.id }
                }
            val existingAdjustment = existingOperation?.let {
                it.accountTransaction ?: it.transactions.firstOrNull()
            }

            val difference = targetBalance - currentBalance

            if (existingAdjustment == null) {
                operationRepository.createOperation(
                    title = null,
                    date = adjustmentDate,
                    categoryId = null,
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

            // Both models must be updated: the legacy leg AND the ledger entries.
            // updateOperation rewrites only the ledger (rewriteEntries) and never the
            // legacy `transactions` row, so calling it alone leaves the legacy leg
            // permanently stale (D17).
            val updated = existingAdjustment.copy(amount = newAmount)
            repository.update(updated)
            existingAdjustment.operationId?.let { operationId ->
                operationRepository.updateOperation(
                    id = operationId,
                    transaction = updated
                )
            }
        }.bind()
    }
}