package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.neoutils.finsight.domain.error.TransferError
import com.neoutils.finsight.domain.error.TransferException
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

/**
 * [ValidateTransferUseCase] owns the rules, and this reads the same ones
 * [TransferBetweenAccountsUseCase] reads — which is what keeps registering a transfer and
 * correcting one from drifting apart.
 */
class UpdateTransferUseCaseImpl(
    private val transactionRepository: ITransactionRepository,
    private val validateTransfer: ValidateTransferUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) : UpdateTransferUseCase {

    override suspend fun invoke(
        transactionId: Long,
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        title: String?,
        destinationAmount: Double?,
    ): Either<TransferException, Unit> = either {
        val (sourceAccount, destinationAccount) = validateTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
        ).mapLeft { TransferException(it) }.bind()

        // Read only to refuse correcting an operation that is not there — nothing of it
        // is carried forward: every field the correction writes comes from the form.
        ensureNotNull(transactionRepository.getTransactionById(transactionId)) {
            TransferException(TransferError.Unknown)
        }

        val arriving = destinationAmount ?: amount

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
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
                contra = null,
            )
        }.mapLeft {
            TransferException(TransferError.Unknown)
        }.bind()

        // The rate this correction applies, observed exactly as the creation observes
        // one: after the write, from the operation's own two ends, and not undone if it
        // fails. Nothing in the archive is read or removed — a rate is an observation
        // about a day and does not belong to the operation that revealed it, so the one
        // that was harvested before stays where it is (design D5). Same pair, same date
        // and same origin means the same key, and the archive replaces it by itself.
        catch {
            harvestExchangeRate(
                sourceAmount = amount,
                sourceCurrency = sourceAccount.currency,
                targetAmount = arriving,
                targetCurrency = destinationAccount.currency,
                date = date,
            )
        }

        Unit
    }
}
