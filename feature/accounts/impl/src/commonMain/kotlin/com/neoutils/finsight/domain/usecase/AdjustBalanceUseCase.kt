package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.model.Account
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.naturalBalanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate
import kotlinx.datetime.yearMonth

class AdjustBalanceUseCase(
    private val transactionRepository: ITransactionRepository,
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

            // Idempotency over the ledger: the existing adjustment is the transaction on
            // this date with a leg on this account and an EQUITY (reconciliation)
            // counter-leg — the ledger shape of "an account adjustment".
            val existingTransaction = transactionRepository
                .observeTransactionsBy(date = adjustmentDate, accountId = account.id)
                .first()
                .firstOrNull { transaction ->
                    transaction.entries.any { it.account.type == AccountType.EQUITY } &&
                        transaction.entries.any { it.account.id == account.id }
                }

            val difference = targetBalance - currentBalance

            if (existingTransaction == null) {
                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = null,
                        date = adjustmentDate,
                        legs = listOf(
                            TransactionLeg(
                                type = TransactionType.ADJUSTMENT,
                                amount = difference,
                                accountId = account.id,
                            )
                        ),
                        // An adjustment's counterpart is reconciliation — equity, by
                        // nature, which is all the ledger needs to be told.
                        contra = ContraLeg(AccountType.EQUITY),
                    )
                )
                return@catch
            }

            // The adjustment's current size is read back from its own ledger leg, so a
            // re-adjustment can never accumulate onto a stale value (D17).
            val currentAdjustment = existingTransaction.entries.naturalBalanceOf(account.id) / 100.0
            val newAmount = currentAdjustment + difference

            if (newAmount == 0.0) {
                transactionRepository.deleteTransactionById(existingTransaction.id)
                return@catch
            }

            transactionRepository.updateTransaction(
                id = existingTransaction.id,
                title = existingTransaction.title,
                date = existingTransaction.date,
                leg = TransactionLeg(
                    type = TransactionType.ADJUSTMENT,
                    amount = newAmount,
                    accountId = account.id,
                ),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}
