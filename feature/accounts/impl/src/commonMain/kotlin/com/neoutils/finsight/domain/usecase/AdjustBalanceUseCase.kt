package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.OperationIntent
import com.neoutils.finsight.domain.model.OperationLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.extension.naturalBalanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class AdjustBalanceUseCase(
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

            // Idempotency over the ledger: the existing adjustment is the operation on
            // this date with a leg on this account and an EQUITY (reconciliation)
            // counter-leg — the ledger shape of "an account adjustment".
            val existingOperation = operationRepository
                .observeOperationsBy(date = adjustmentDate, accountId = account.id)
                .first()
                .firstOrNull { operation ->
                    operation.entries.any { it.account.type == AccountType.EQUITY } &&
                        operation.entries.any { it.account.id == account.id }
                }

            val difference = targetBalance - currentBalance

            if (existingOperation == null) {
                operationRepository.createOperation(
                    OperationIntent(
                        title = null,
                        date = adjustmentDate,
                        legs = listOf(
                            OperationLeg(
                                type = TransactionType.ADJUSTMENT,
                                amount = difference,
                                account = account,
                            )
                        ),
                    )
                )
                return@catch
            }

            // The adjustment's current size is read back from its own ledger leg, so a
            // re-adjustment can never accumulate onto a stale value (D17).
            val currentAdjustment = existingOperation.entries.naturalBalanceOf(account.id) / 100.0
            val newAmount = currentAdjustment + difference

            if (newAmount == 0.0) {
                operationRepository.deleteOperationById(existingOperation.id)
                return@catch
            }

            operationRepository.updateOperation(
                id = existingOperation.id,
                title = existingOperation.title,
                date = existingOperation.date,
                leg = OperationLeg(
                    type = TransactionType.ADJUSTMENT,
                    amount = newAmount,
                    account = account,
                ),
            )
        }.bind()
    }
}
