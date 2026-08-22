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
 * Correcting a transfer that is already registered, in place.
 *
 * It is the counterpart of [TransferBetweenAccountsUseCase] and shares its rules
 * wholesale — [ValidateTransferUseCase] owns them, and a transfer is no more or less
 * admissible for having been written once already. What differs is the write: the legs
 * of an existing operation are rewritten rather than created, so the operation keeps
 * its identity instead of becoming a new one.
 *
 * Crossing currencies takes no branch here either. The intent arrives at the boundary
 * incomplete and is completed there, conversion legs and all, exactly as on creation.
 */
class UpdateTransferUseCase(
    private val transactionRepository: ITransactionRepository,
    private val validateTransfer: ValidateTransferUseCase,
    private val harvestExchangeRate: HarvestExchangeRateUseCase,
) {
    /**
     * @param destinationAmount what arrives, when it is not what left. `null` means the
     * two ends are the same number — the whole of the mono-currency case.
     */
    suspend operator fun invoke(
        transactionId: Long,
        sourceAccountId: Long,
        destinationAccountId: Long,
        amount: Double,
        date: LocalDate,
        destinationAmount: Double? = null,
    ): Either<TransferException, Unit> = either {
        val (sourceAccount, destinationAccount) = validateTransfer(
            sourceAccountId = sourceAccountId,
            destinationAccountId = destinationAccountId,
            amount = amount,
            date = date,
            destinationAmount = destinationAmount,
        ).mapLeft { TransferException(it) }.bind()

        val transaction = transactionRepository.getTransactionById(transactionId)
        ensureNotNull(transaction) { TransferException(TransferError.Unknown) }

        val arriving = destinationAmount ?: amount

        catch {
            transactionRepository.updateTransaction(
                id = transactionId,
                // The row is rewritten whole, title included, and the transfer form has
                // no title field. Writing `null` would silently erase a title the screen
                // never showed and never asked about (design D11), so what is there is
                // carried over untouched.
                title = transaction.title,
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
