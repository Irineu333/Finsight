package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.AccountError
import com.neoutils.finsight.domain.exception.AccountException
import com.neoutils.finsight.domain.exception.AccountNotAdjustedException
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import com.neoutils.finsight.extension.naturalBalanceOf
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class AdjustBalanceUseCaseImpl(
    private val accountRepository: IAccountRepository,
    private val transactionRepository: ITransactionRepository,
    private val calculateBalanceUseCase: CalculateBalanceUseCase,
) : AdjustBalanceUseCase {

    @OptIn(WithheldAnnouncement::class)
    override suspend fun invoke(
        targetBalance: Double,
        adjustmentDate: LocalDate,
        accountId: Long,
    ): Either<Throwable, Unit> = either {
        // The account is resolved before anything is read or written, so an identity
        // that matches nothing is a typed refusal here rather than a foreign-key
        // failure at the write boundary.
        ensureNotNull(catch { accountRepository.getAccountById(accountId) }.bind()) {
            AccountException(AccountError.NOT_FOUND)
        }

        val currentBalance = catch {
            // Scoped to one account, so scalar — and the currency is the account's own.
            calculateBalanceUseCase.forAccount(
                accountId = accountId,
                target = adjustmentDate,
            )
        }.bind()

        ensure(targetBalance != currentBalance) { AccountNotAdjustedException() }

        catch {

            // Idempotency over the ledger: the existing adjustment is the transaction on
            // this date with a leg on this account and an EQUITY (reconciliation)
            // counter-leg — the ledger shape of "an account adjustment".
            val existingTransaction = transactionRepository
                .observeTransactionsBy(date = adjustmentDate, accountId = accountId)
                .first()
                .firstOrNull { transaction ->
                    transaction.entries.any { it.account.type == AccountType.EQUITY } &&
                        transaction.entries.any { it.account.id == accountId }
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
                                accountId = accountId,
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
            val currentAdjustment = existingTransaction.entries.naturalBalanceOf(accountId) / 100.0
            val newAmount = currentAdjustment + difference

            if (newAmount == 0.0) {
                // Clearing an adjustment removes a derived figure, not typed work: the row
                // holds the difference between a balance the person stated and one the
                // ledger computed, and stating the balance again reproduces it. That is
                // `DestructiveClass.DERIVED_VALUE`, which the preventive trigger does not
                // cover (design D7 of `automatic-backup`).
                //
                // Withheld rather than left to the trigger to refuse, because the prelude
                // is reached through the two methods every removal shares and cannot tell
                // this one apart: announcing here would take a copy the design excludes,
                // and a copy that failed would abort the adjustment on a screen with no
                // way to go on without one.
                transactionRepository.deleteTransactionById(
                    id = existingTransaction.id,
                    announcement = RemovalAnnouncement.Withheld,
                )
                return@catch
            }

            transactionRepository.updateTransaction(
                id = existingTransaction.id,
                title = existingTransaction.title,
                date = existingTransaction.date,
                legs = listOf(
                    TransactionLeg(
                        type = TransactionType.ADJUSTMENT,
                        amount = newAmount,
                        accountId = accountId,
                    ),
                ),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}
