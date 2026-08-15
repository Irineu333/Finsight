package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.model.AccountType
import com.neoutils.finsight.domain.model.ContraLeg
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.TransactionIntent
import com.neoutils.finsight.domain.model.TransactionLeg
import com.neoutils.finsight.domain.model.TransactionType
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

/**
 * Restates what an invoice owes on a date, by writing — or rewriting, or removing —
 * the single reconciliation transaction that stands for the difference.
 *
 * It is the sole owner of "adjust an invoice", and it is the same mechanism as
 * adjusting an account: one leg and an `EQUITY` counter-leg. What distinguishes the
 * two is only where the dimension lands, not their nature. Re-adjusting reads the
 * existing size back off its own leg rather than accumulating onto a stale figure.
 *
 * **Public contract.** An [Invoice], a target and a date in, `Unit` out, and no
 * presentation type anywhere — no `UiText`, no string resource. A concrete class rather
 * than an interface, because it depends only on `:core:ledger` and on this same `api`.
 *
 * The failure channel is `Either<Throwable, Unit>`, which is weaker than the named
 * error types this project prefers: [InvoiceNotAdjustedException] — "the invoice
 * already owes what you asked for" — arrives on the same channel as a database failure,
 * so a consumer that wants to tell them apart has to test the type. That is the
 * contract as it stands, recorded rather than widened here, because narrowing it is a
 * change to what the screens already handle.
 */
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
                    accountId = invoice.creditCard.accountId,
                    dimensionId = invoice.dimensionId,
                ),
                contra = ContraLeg(AccountType.EQUITY),
            )
        }.bind()
    }
}
