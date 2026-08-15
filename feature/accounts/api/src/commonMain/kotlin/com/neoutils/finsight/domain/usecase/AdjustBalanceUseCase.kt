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

/**
 * Restates an account's balance on a date, by writing — or rewriting, or removing —
 * the single reconciliation transaction that stands for the difference.
 *
 * It is the sole owner of "adjust an account": the ledger shape of an adjustment is
 * one leg on the account and an `EQUITY` counter-leg, and re-adjusting reads the
 * existing size back off that leg instead of accumulating onto a stale figure.
 *
 * **Public contract.** It lives in the `api` because more than one module invokes it,
 * and the boundary was reviewed as such: identities, a date and an [Account] in,
 * `Unit` out, and no presentation type anywhere — no `UiText`, no string resource.
 * A concrete class rather than an interface, because it depends only on `:core:*`.
 *
 * The failure channel is `Either<Throwable, Unit>`, which is weaker than the named
 * error types this project prefers: [AccountNotAdjustedException] — "the balance is
 * already the one you asked for" — arrives on the same channel as a database failure,
 * so a consumer that wants to tell them apart has to test the type. That is the
 * contract as it stands, recorded rather than widened here, because narrowing it is a
 * change to what the screens already handle.
 */
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
            // Scoped to one account, so scalar — and the currency is the account's own.
            calculateBalanceUseCase.forAccount(
                accountId = account.id,
                target = adjustmentDate,
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
