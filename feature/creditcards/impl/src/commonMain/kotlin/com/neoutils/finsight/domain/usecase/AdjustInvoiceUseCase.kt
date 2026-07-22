package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.model.AccountType
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
                                creditCard = invoice.creditCard,
                                invoice = invoice,
                            )
                        ),
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
                    creditCard = invoice.creditCard,
                    invoice = invoice,
                ),
            )
        }.bind()
    }
}
