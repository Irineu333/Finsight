package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

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
 * What makes a transfer admissible is [ValidateTransferUseCase]'s, and this use case
 * only registers what it approved.
 */
class TransferBetweenAccountsUseCase(
    private val transactionRepository: ITransactionRepository,
    private val validateTransfer: ValidateTransferUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) {
    /**
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number, which is the whole of the mono-currency case and
     * stays byte-identical to what it was.
     * @param title why the money moved, as the user stated it, and `null` when they had
     * nothing to state. It names the operation and classifies nothing: a transfer has no
     * analytic axis, and a title does not give it one.
     */
    suspend operator fun invoke(
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double? = null,
        title: String? = null,
    ): Either<TransferException, Transaction> = either {
        val (sourceAccount, destinationAccount) = validateTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
        ).mapLeft { TransferException(it) }.bind()

        // The two ends are the same number unless the caller said otherwise. A
        // same-currency transfer therefore cannot be given two, and a cross-currency one
        // that arrives without a second value simply moves the same figure — which the
        // write boundary will then balance through the conversion accounts.
        val arriving = destinationAmount ?: amount

        val transaction = catch {
            transactionRepository.createTransaction(
                TransactionIntent(
                    title = title,
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
