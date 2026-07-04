package com.neoutils.finsight.domain.usecase

import arrow.core.Either
import arrow.core.Either.Companion.catch
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.neoutils.finsight.domain.exception.InvoiceNotAdjustedException
import com.neoutils.finsight.domain.model.Invoice
import com.neoutils.finsight.domain.model.Operation
import com.neoutils.finsight.domain.model.Transaction
import com.neoutils.finsight.domain.repository.IOperationRepository
import com.neoutils.finsight.domain.repository.ITransactionRepository
import kotlinx.datetime.LocalDate

class AdjustInvoiceUseCase(
    private val repository: ITransactionRepository,
    private val operationRepository: IOperationRepository,
    private val calculateInvoiceUseCase: CalculateInvoiceUseCase,
) {
    suspend operator fun invoke(
        invoice: Invoice,
        target: Double,
        adjustmentDate: LocalDate
    ): Either<Throwable, Unit> = either {
        val currentInvoice = catch {
            calculateInvoiceUseCase(invoiceId = invoice.id)
        }.bind()

        ensure(target != currentInvoice) { InvoiceNotAdjustedException() }

        val existingAdjustment = catch {
            repository.getTransactionsBy(
                type = Transaction.Type.ADJUSTMENT,
                target = Transaction.Target.CREDIT_CARD,
                invoiceId = invoice.id,
                date = adjustmentDate
            ).firstOrNull()
        }.bind()

        val difference = target - currentInvoice

        catch {
            if (existingAdjustment == null) {
                operationRepository.createOperation(
                    kind = Operation.Kind.TRANSACTION,
                    title = null,
                    date = adjustmentDate,
                    categoryId = null,
                    sourceAccountId = null,
                    targetCreditCardId = invoice.creditCard.id,
                    targetInvoiceId = invoice.id,
                    transactions = listOf(
                        Transaction(
                            title = null,
                            type = Transaction.Type.ADJUSTMENT,
                            amount = -difference,
                            date = adjustmentDate,
                            target = Transaction.Target.CREDIT_CARD,
                            creditCard = invoice.creditCard,
                            invoice = invoice
                        )
                    ),
                )
                return@catch
            }

            val newAmount = existingAdjustment.amount - difference

            if (newAmount == 0.0) {
                val operationId = existingAdjustment.operationId
                if (operationId != null) {
                    operationRepository.deleteOperationById(operationId)
                } else {
                    repository.delete(existingAdjustment)
                }
                return@catch
            }

            repository.update(
                existingAdjustment.copy(amount = newAmount)
            )
        }.bind()
    }
}