package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.ledger.RemovalAnnouncement
import com.neoutils.finsight.domain.ledger.WithheldAnnouncement
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

class AdjustInvoiceUseCase(
    private val transactionRepository: ITransactionRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    @OptIn(WithheldAnnouncement::class)
    suspend operator fun invoke(
        invoice: Invoice,
        target: Double,
        adjustmentDate: LocalDate
    ): Either<Throwable, Unit> = either {
        val currentInvoice = catch {
            calculateInvoiceUseCase(invoice)
        }.bind()

        ensure(target != currentInvoice) { InvoiceNotAdjustedException() }

        catch {

            // Idempotency over the ledger: the existing adjustment is the transaction on
            // this date carrying this invoice and an EQUITY (reconciliation)
            // counter-leg — the ledger shape of "an invoice adjustment".
            val existingTransaction = transactionRepository
                .observeTransactionsBy(date = adjustmentDate, dimensionId = invoice.dimensionId)
                .first()
                .firstOrNull { transaction ->
                    transaction.entries.any { it.account.type == AccountType.EQUITY }
                }

            val difference = target - currentInvoice

            if (existingTransaction == null) {
                transactionRepository.createTransaction(
                    TransactionIntent(
                        title = null,
                        date = adjustmentDate,
                        legs = listOf(
                            TransactionLeg(
                                type = TransactionType.ADJUSTMENT,
                                amount = -difference,
                                accountId = invoice.creditCard.accountId,
                                dimensionId = invoice.dimensionId,
                            )
                        ),
                        contra = ContraLeg(AccountType.EQUITY),
                    )
                )
                return@catch
            }

            // The adjustment's current size is read back from its own ledger leg, so a
            // re-adjustment can never accumulate onto a stale value (D17).
            val currentAdjustment = existingTransaction.entries
                .filter { it.dimensionId == invoice.dimensionId }
                .sumOf { it.amount } / 100.0
            val newAmount = currentAdjustment - difference

            if (newAmount == 0.0) {
                // Clearing an adjustment removes a derived figure, not typed work: the row
                // holds the difference between an invoice total the person stated and one
                // the ledger computed, and stating the total again reproduces it. That is
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
                        accountId = invoice.creditCard.accountId,
                        dimensionId = invoice.dimensionId,
                    ),
                ),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}
