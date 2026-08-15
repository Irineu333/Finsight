@file:OptIn(ExperimentalTime::class)

package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.IAccountRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

private val currentDate
    get() = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date

/**
 * Money moving between two of the user's own accounts.
 *
 * **When the two differ in currency the caller states both ends**, because that is what
 * the statement shows: R$ 550 left here, US$ 100 arrived there. No rate is a parameter
 * anywhere on this path — the rate is a quotient of the two ends and is *derived* from
 * them afterwards (design D6). The write boundary is what completes the intent, posting
 * the residue of each currency to that currency's conversion account, so nothing here
 * has to know how a cross-currency transaction balances.
 *
 * **Public contract.** It lives in the `api` because more than one module invokes it,
 * and the boundary was reviewed as such: identities and primitives in, `Transaction`
 * out, and the failure channel is [TransferException] wrapping [TransferError] — a
 * domain error carrying an English `message` for logs. Nothing here names a
 * presentation type: `toUiText()` is an extension in `:core:model` that a screen opts
 * into, never something this signature hands out. A concrete class rather than an
 * interface, because it depends only on `:core:*` and on this same `api`.
 */
class TransferBetweenAccountsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val accountRepository: IAccountRepository,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) {
    /**
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number, which is the whole of the mono-currency case and
     * stays byte-identical to what it was.
     */
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double? = null,
    ): Either<TransferException, Transaction> = either {
        ensure(amount > 0.0) {
            TransferException(TransferError.InvalidAmount)
        }

        ensure(destinationAmount == null || destinationAmount > 0.0) {
            TransferException(TransferError.InvalidAmount)
        }

        ensure(sourceAccountId != destinationAccountId) {
            TransferException(TransferError.SameAccount)
        }

        ensure(date <= currentDate) {
            TransferException(TransferError.FutureDate)
        }

        val sourceAccount = accountRepository.getAccountById(sourceAccountId)
        ensureNotNull(sourceAccount) {
            TransferException(TransferError.SourceAccountNotFound)
        }

        val destinationAccount = accountRepository.getAccountById(destinationAccountId)
        ensureNotNull(destinationAccount) {
            TransferException(TransferError.DestinationAccountNotFound)
        }

        // The two ends are the same number unless the caller said otherwise. A
        // same-currency transfer therefore cannot be given two, and a cross-currency one
        // that arrives without a second value simply moves the same figure — which the
        // write boundary will then balance through the conversion accounts.
        val arriving = destinationAmount ?: amount

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = null,
                    date = date,
                    legs = listOf(
                        TransactionLeg(
                            type = TransactionType.EXPENSE,
                            amount = amount,
                            accountId = sourceAccount.id,
                        ),
                        TransactionLeg(
                            type = TransactionType.INCOME,
                            amount = arriving,
                            accountId = destinationAccount.id,
                        ),
                    ),
                )
            )
        }.mapLeft {
            TransferException(TransferError.Unknown)
        }.bind()

        // The rate the operation just applied, learned from its own two ends and written
        // to the archive — not to the transaction, which has no rate field (design D11).
        // Deliberately after the write and deliberately not undone if it fails: a rate is
        // an observation about a day, and it outlives the operation that revealed it.
        catch {
            harvestExchangeRate(
                sourceAmount = amount,
                sourceCurrency = sourceAccount.currency,
                targetAmount = arriving,
                targetCurrency = destinationAccount.currency,
                date = date,
            )
        }

        transaction
    }
}
